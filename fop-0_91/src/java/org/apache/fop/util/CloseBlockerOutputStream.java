/*
 * Copyright 1999-2004 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* $Id$ */
 
package org.apache.fop.util;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * This is a decorator to block calls to close() to the underlying stream.
 */
public class CloseBlockerOutputStream extends FilterOutputStream {

    /**
     * @see java.io.FilterOutputStream#FilterOutputStream(OutputStream)
     */
    public CloseBlockerOutputStream(OutputStream out) {
        super(out);
    }

    /**
     * @see java.io.OutputStream#close()
     */
    public void close() throws IOException {
        try {
            flush();
        } catch (IOException ioe) {
            //ignore
        }
    }

}
