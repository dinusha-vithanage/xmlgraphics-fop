/*
 * Copyright 1999-2005 The Apache Software Foundation.
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

package org.apache.fop.layoutmgr.table;

import org.apache.fop.datatypes.Length;
import org.apache.fop.datatypes.PercentBase;
import org.apache.fop.fo.flow.Table;
import org.apache.fop.fo.flow.TableColumn;
import org.apache.fop.fo.properties.TableColLength;
import org.apache.fop.layoutmgr.BlockLevelLayoutManager;
import org.apache.fop.layoutmgr.BlockStackingLayoutManager;
import org.apache.fop.layoutmgr.KnuthElement;
import org.apache.fop.layoutmgr.KnuthGlue;
import org.apache.fop.layoutmgr.KnuthPenalty;
import org.apache.fop.layoutmgr.LayoutContext;
import org.apache.fop.layoutmgr.PositionIterator;
import org.apache.fop.layoutmgr.Position;
import org.apache.fop.layoutmgr.TraitSetter;
import org.apache.fop.area.Area;
import org.apache.fop.area.Block;
import org.apache.fop.traits.MinOptMax;
import org.apache.fop.traits.SpaceVal;

import java.util.Iterator;
import java.util.LinkedList;
import org.apache.fop.datatypes.LengthBase;
import org.apache.fop.fo.FObj;

/**
 * LayoutManager for a table FO.
 * A table consists of oldColumns, table header, table footer and multiple
 * table bodies.
 * The header, footer and body add the areas created from the table cells.
 * The table then creates areas for the oldColumns, bodies and rows
 * the render background.
 */
public class TableLayoutManager extends BlockStackingLayoutManager 
                implements BlockLevelLayoutManager {
    private Table fobj;
    
    private TableContentLayoutManager contentLM; 
    private ColumnSetup columns = null;

    private Block curBlockArea;

    private int referenceIPD;
    private int referenceBPD;
    private boolean autoLayout = true;

    //TODO space-before|after: handle space-resolution rules
    private MinOptMax spaceBefore;
    private MinOptMax spaceAfter;
    
    /**
     * Create a new table layout manager.
     * @param node the table FO
     */
    public TableLayoutManager(Table node) {
        super(node);
        fobj = node;
        this.columns = new ColumnSetup(node);
    }

    /** @return the table FO */
    public Table getTable() {
        return this.fobj;
    }
    
    /**
     * @return the column setup for this table.
     */
    public ColumnSetup getColumns() {
        return this.columns;
    }
    
    public void initialize() {
        spaceBefore = new SpaceVal(fobj.getCommonMarginBlock().spaceBefore, this).getSpace();
        spaceAfter = new SpaceVal(fobj.getCommonMarginBlock().spaceAfter, this).getSpace();
        
        if (!fobj.isAutoLayout() 
                && fobj.getInlineProgressionDimension().getOptimum(this).getEnum() != EN_AUTO) {
            autoLayout = false;
        }
    }

    private int getIPIndents() {
        int iIndents = 0;
        iIndents += fobj.getCommonMarginBlock().startIndent.getValue(this);
        iIndents += fobj.getCommonMarginBlock().endIndent.getValue(this);
        return iIndents;
    }
    
    /** @see org.apache.fop.layoutmgr.LayoutManager */
    public LinkedList getNextKnuthElements(LayoutContext context, int alignment) {
        
        LinkedList returnList = new LinkedList();
        
        if (!bBreakBeforeServed) {
            try {
                if (addKnuthElementsForBreakBefore(returnList)) {
                    return returnList;
                }
            } finally {
                bBreakBeforeServed = true;
            }
        }

        referenceBPD = context.getStackLimit().opt;
        referenceIPD = context.getRefIPD();

        if (fobj.getInlineProgressionDimension().getOptimum(this).getEnum() != EN_AUTO) {
            referenceIPD = fobj.getInlineProgressionDimension().getOptimum(this).getLength().getValue(this);
        } else if( !fobj.isAutoLayout() ) {
            log.info("table-layout=\"fixed\" and width=\"auto\", but auto-layout not supported " + 
                     "=> assuming width=\"100%\"");
        }
        if (referenceIPD > context.getRefIPD()) {
            log.warn("Allocated IPD exceeds available reference IPD");
        }
        int contentIPD = referenceIPD - getIPIndents();

        MinOptMax stackSize = new MinOptMax();
        //Add spacing
        if (spaceAfter != null) {
            stackSize.add(spaceAfter);
        }
        if (spaceBefore != null) {
            stackSize.add(spaceBefore);
        }

        // either works out table of column widths or if proportional-column-width function
        // is used works out total factor, so that value of single unit can be computed.
        int sumCols = 0;
        float factors = 0;
        for (Iterator i = columns.iterator(); i.hasNext(); ) {
            TableColumn column = (TableColumn) i.next();
            Length width = column.getColumnWidth();
            sumCols += width.getValue(this);
            if (width instanceof TableColLength) {
                factors += ((TableColLength) width).getTableUnits();
            }
        }
        // sets TABLE_UNITS in case where one or more oldColumns is defined using 
        // proportional-column-width
        if (sumCols < contentIPD) {
            if (fobj.getLayoutDimension(PercentBase.TABLE_UNITS).floatValue() == 0.0) {
                fobj.setLayoutDimension(PercentBase.TABLE_UNITS,
                                      (contentIPD - sumCols) / factors);
            }
        }

        LinkedList returnedList = null;
        LinkedList contentList = new LinkedList();
        //Position returnPosition = new NonLeafPosition(this, null);
        //Body prevLM = null;

        LayoutContext childLC = new LayoutContext(0);
        childLC.setStackLimit(
              MinOptMax.subtract(context.getStackLimit(),
                                 stackSize));
        childLC.setRefIPD(context.getRefIPD());

        if (contentLM == null) {
            contentLM = new TableContentLayoutManager(this);
        }
        returnedList = contentLM.getNextKnuthElements(childLC, alignment);
        if (childLC.isKeepWithNextPending()) {
            log.debug("TableContentLM signals pending keep-with-next");
            context.setFlags(LayoutContext.KEEP_WITH_NEXT_PENDING);
        }
        if (childLC.isKeepWithPreviousPending()) {
            log.debug("TableContentLM signals pending keep-with-previous");
            context.setFlags(LayoutContext.KEEP_WITH_PREVIOUS_PENDING);
        }
        
        //Set index values on elements coming from the content LM
        Iterator iter = returnedList.iterator();
        while (iter.hasNext()) {
            KnuthElement el = (KnuthElement)iter.next();
            notifyPos(el.getPosition());
        }
        log.debug(returnedList);
        
        if (returnedList.size() == 1
                && ((KnuthElement) returnedList.getFirst()).isPenalty()
                && ((KnuthPenalty) returnedList.getFirst()).getP() == -KnuthElement.INFINITE) {
            // a descendant of this block has break-before
            if (returnList.size() == 0) {
                // the first child (or its first child ...) has
                // break-before;
                // all this block, including space before, will be put in
                // the
                // following page
                //FIX ME
                //bSpaceBeforeServed = false;
            }
            contentList.addAll(returnedList);

            // "wrap" the Position inside each element
            // moving the elements from contentList to returnList
            returnedList = new LinkedList();
            wrapPositionElements(contentList, returnList);

            return returnList;
        } else {
            /*
            if (prevLM != null) {
                // there is a block handled by prevLM
                // before the one handled by curLM
                if (mustKeepTogether() 
                        || prevLM.mustKeepWithNext()
                        || curLM.mustKeepWithPrevious()) {
                    // add an infinite penalty to forbid a break between
                    // blocks
                    contentList.add(new KnuthPenalty(0,
                            KnuthElement.INFINITE, false,
                            new Position(this), false));
                } else if (!((KnuthElement) contentList.getLast()).isGlue()) {
                    // add a null penalty to allow a break between blocks
                    contentList.add(new KnuthPenalty(0, 0, false,
                            new Position(this), false));
                } else {
                    // the last element in contentList is a glue;
                    // it is a feasible breakpoint, there is no need to add
                    // a penalty
                }
            }*/
            contentList.addAll(returnedList);
            if (returnedList.size() > 0) {
                if (((KnuthElement) returnedList.getLast()).isPenalty()
                        && ((KnuthPenalty) returnedList.getLast()).getP() == -KnuthElement.INFINITE) {
                    // a descendant of this block has break-after
                    if (false /*curLM.isFinished()*/) {
                        // there is no other content in this block;
                        // it's useless to add space after before a page break
                        setFinished(true);
                    }

                    returnedList = new LinkedList();
                    wrapPositionElements(contentList, returnList);

                    return returnList;
                }
            }
        }
        wrapPositionElements(contentList, returnList);
        addKnuthElementsForBreakAfter(returnList);
        setFinished(true);
        return returnList;
    }
    
    /**
     * The table area is a reference area that contains areas for
     * oldColumns, bodies, rows and the contents are in cells.
     *
     * @param parentIter the position iterator
     * @param layoutContext the layout context for adding areas
     */
    public void addAreas(PositionIterator parentIter,
                         LayoutContext layoutContext) {
        getParentArea(null);
        getPSLM().addIDToPage(fobj.getId());

        // if adjusted space before
        double adjust = layoutContext.getSpaceAdjust();
        addBlockSpacing(adjust, spaceBefore);
        spaceBefore = null;

        int startXOffset = fobj.getCommonMarginBlock().startIndent.getValue(this);
        
        // add column, body then row areas

        int tableHeight = 0;
        //Body childLM;
        LayoutContext lc = new LayoutContext(0);


        lc.setRefIPD(referenceIPD - getIPIndents());
        contentLM.setStartXOffset(startXOffset);
        contentLM.addAreas(parentIter, lc);
        tableHeight += contentLM.getUsedBPD();

        curBlockArea.setBPD(tableHeight);

        if (fobj.isSeparateBorderModel()) {
            TraitSetter.addBorders(curBlockArea, fobj.getCommonBorderPaddingBackground(), this);
        }
        TraitSetter.addBackground(curBlockArea, 
                fobj.getCommonBorderPaddingBackground(),
                this);
        TraitSetter.addMargins(curBlockArea,
                fobj.getCommonBorderPaddingBackground(), 
                fobj.getCommonMarginBlock(),
                this);
        TraitSetter.addBreaks(curBlockArea, 
                fobj.getBreakBefore(), fobj.getBreakAfter());

        flush();

        // if adjusted space after
        addBlockSpacing(adjust, spaceAfter);

        //bodyBreaks.clear();
        curBlockArea = null;
    }

    /**
     * Return an Area which can contain the passed childArea. The childArea
     * may not yet have any content, but it has essential traits set.
     * In general, if the LayoutManager already has an Area it simply returns
     * it. Otherwise, it makes a new Area of the appropriate class.
     * It gets a parent area for its area by calling its parent LM.
     * Finally, based on the dimensions of the parent area, it initializes
     * its own area. This includes setting the content IPD and the maximum
     * BPD.
     *
     * @param childArea the child area
     * @return the parent area of the child
     */
    public Area getParentArea(Area childArea) {
        if (curBlockArea == null) {
            curBlockArea = new Block();
            // Set up dimensions
            // Must get dimensions from parent area
            /*Area parentArea =*/ parentLM.getParentArea(curBlockArea);
            
            TraitSetter.setProducerID(curBlockArea, getTable().getId());

            int contentIPD = referenceIPD - getIPIndents();
            curBlockArea.setIPD(contentIPD);
            
            setCurrentArea(curBlockArea);
        }
        return curBlockArea;
    }

    /**
     * Add the child area to this layout manager.
     *
     * @param childArea the child area to add
     */
    public void addChildArea(Area childArea) {
        if (curBlockArea != null) {
            curBlockArea.addBlock((Block) childArea);
        }
    }

    /**
     * Reset the position of this layout manager.
     *
     * @param resetPos the position to reset to
     */
    public void resetPosition(Position resetPos) {
        if (resetPos == null) {
            reset(null);
        }
    }

    /**
     * @see org.apache.fop.layoutmgr.BlockLevelLayoutManager#negotiateBPDAdjustment(int, org.apache.fop.layoutmgr.KnuthElement)
     */
    public int negotiateBPDAdjustment(int adj, KnuthElement lastElement) {
        // TODO Auto-generated method stub
        return 0;
    }

    /**
     * @see org.apache.fop.layoutmgr.BlockLevelLayoutManager#discardSpace(org.apache.fop.layoutmgr.KnuthGlue)
     */
    public void discardSpace(KnuthGlue spaceGlue) {
        // TODO Auto-generated method stub
        
    }

    /**
     * @see org.apache.fop.layoutmgr.BlockLevelLayoutManager#mustKeepTogether()
     */
    public boolean mustKeepTogether() {
        //TODO Keeps will have to be more sophisticated sooner or later
        return ((BlockLevelLayoutManager)getParent()).mustKeepTogether() 
                || !fobj.getKeepTogether().getWithinPage().isAuto()
                || !fobj.getKeepTogether().getWithinColumn().isAuto();
    }

    /**
     * @see org.apache.fop.layoutmgr.BlockLevelLayoutManager#mustKeepWithPrevious()
     */
    public boolean mustKeepWithPrevious() {
        return !fobj.getKeepWithPrevious().getWithinPage().isAuto()
                || !fobj.getKeepWithPrevious().getWithinColumn().isAuto();
    }

    /**
     * @see org.apache.fop.layoutmgr.BlockLevelLayoutManager#mustKeepWithNext()
     */
    public boolean mustKeepWithNext() {
        return !fobj.getKeepWithNext().getWithinPage().isAuto()
                || !fobj.getKeepWithNext().getWithinColumn().isAuto();
    }

    // --------- Property Resolution related functions --------- //

    /**
     * @see org.apache.fop.datatypes.PercentBaseContext#getBaseLength(int, FObj)
     */
    public int getBaseLength(int lengthBase, FObj fobj) {
        // Special handler for TableColumn width specifications
        if (fobj instanceof TableColumn && fobj.getParent() == getFObj()) {
            switch (lengthBase) {
            case LengthBase.CONTAINING_BLOCK_WIDTH:
                return getContentAreaIPD();
            default:
                log.error("Unknown base type for LengthBase.");
                return 0;
            }
        } else {
            return super.getBaseLength(lengthBase, fobj);
        }
    }
    
    /**
     * Returns the IPD of the content area
     * @return the IPD of the content area
     */
    public int getContentAreaIPD() {
        return referenceIPD;
    }
   
    /**
     * Returns the BPD of the content area
     * @return the BPD of the content area
     */
    public int getContentAreaBPD() {
        return referenceBPD;
    }
    
}

