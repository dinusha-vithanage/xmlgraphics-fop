/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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

package org.apache.fop.render.ps.svg;

import java.util.ArrayList;
import java.util.List;

import org.apache.fop.render.shading.Function;
import org.apache.fop.render.shading.Pattern;
import org.apache.fop.render.shading.Shading;

public class PSPattern implements Pattern {

    /**
     * Either one (1) for tiling, or two (2) for shading.
     */
    protected int patternType = 2;      // Default

    /**
     * The Shading object comprising the Type 2 pattern
     */
    protected Shading shading;

    /**
     * List of Integers represetning the Extended unique Identifier
     */
    protected List xUID = null;

    /**
     * TODO use PDFGState
     * String representing the extended Graphics state.
     * Probably will never be used like this.
     */
    protected StringBuffer extGState = null;

    private final List<Double> matrix;

    /**
     * Creates a radial or axial shading pattern
     * @param thePatternType The pattern type which will be 3 for radial and 2 for axial
     * @param theShading The shading object to determine how the gradient
     * is drawn
     * @param theXUID The XUID
     * @param theExtGState The exit state
     */
    public PSPattern(int thePatternType, Shading theShading, List theXUID,
                     StringBuffer theExtGState, List<Double> matrix) {
        this.patternType = 2;             // thePatternType;
        this.shading = theShading;
        this.xUID = theXUID;
        this.extGState = theExtGState;    // always null
        this.matrix = matrix;
    }

    /**
     * Outputs the radial or axial pattern as a string dictionary to insert
     * into a postscript document.
     */
    public String toString() {
        int vectorSize = 0;
        int tempInt = 0;
        StringBuilder p = new StringBuilder(64);
        p.append("/Pattern setcolorspace\n");
        p.append("<< \n/Type /Pattern \n");

        p.append("/PatternType " + this.patternType + " \n");

        if (this.shading != null) {
            p.append("/Shading ");
            outputShading(p);
            p.append(" \n");
        }

        if (this.xUID != null) {
            vectorSize = this.xUID.size();
            p.append("/XUID [ ");
            for (tempInt = 0; tempInt < vectorSize; tempInt++) {
                p.append((this.xUID.get(tempInt)) + " ");
            }
            p.append("] \n");
        }

        if (this.extGState != null) {
            p.append("/ExtGState " + this.extGState + " \n");
        }

        p.append(">> \n");
        p.append("[ ");
        for (double m : matrix) {
            p.append(Double.toString(m)); // TODO refactor so that PSGenerator.formatDouble can be used
            p.append(" ");
        }
        p.append("] ");
        p.append("makepattern setcolor\n");

        return p.toString();
    }

    private void outputShading(StringBuilder p) {
        final Function function = shading.getFunction();
        Shading.FunctionRenderer functionRenderer = new Shading.FunctionRenderer() {

            public void outputFunction(StringBuilder out) {
                List<String> functionsStrings = new ArrayList<String>(function.getFunctions().size());
                for (Function f : function.getFunctions()) {
                    functionsStrings.add(functionToString(f));
                }
                out.append(function.toWriteableString(functionsStrings));
            }
        };
        shading.output(p, functionRenderer);
    }

    private String functionToString(Function function) {
        List<String> functionsStrings = new ArrayList<String>(function.getFunctions().size());
        for (Function f : function.getFunctions()) {
            functionsStrings.add(functionToString(f));
        }
        return function.toWriteableString(functionsStrings);
    }

}
