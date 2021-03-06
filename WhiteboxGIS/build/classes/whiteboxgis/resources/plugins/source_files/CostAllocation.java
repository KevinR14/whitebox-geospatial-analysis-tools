/*
 * Copyright (C) 2011-2012 Dr. John Lindsay <jlindsay@uoguelph.ca>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package plugins;

import java.util.Date;
import whitebox.geospatialfiles.WhiteboxRaster;
import whitebox.interfaces.WhiteboxPlugin;
import whitebox.interfaces.WhiteboxPluginHost;

/**
 * WhiteboxPlugin is used to define a plugin tool for Whitebox GIS.
 * @author Dr. John Lindsay <jlindsay@uoguelph.ca>
 */
public class CostAllocation implements WhiteboxPlugin {
    
    private WhiteboxPluginHost myHost = null;
    private String[] args;
    /**
     * Used to retrieve the plugin tool's name. This is a short, unique name
     * containing no spaces.
     *
     * @return String containing plugin name.
     */
    @Override
    public String getName() {
        return "CostAllocation";
    }
    /**
     * Used to retrieve the plugin tool's descriptive name. This can be a longer name (containing spaces) and is used in the interface to list the tool.
     * @return String containing the plugin descriptive name.
     */
    @Override
    public String getDescriptiveName() {
    	return "Cost Allocation";
    }
    /**
     * Used to retrieve a short description of what the plugin tool does.
     * @return String containing the plugin's description.
     */
    @Override
    public String getToolDescription() {
    	return "Performs cost-distance source allocation.";
    }
    /**
     * Used to identify which toolboxes this plugin tool should be listed in.
     * @return Array of Strings.
     */
    @Override
    public String[] getToolbox() {
    	String[] ret = { "CostTools" };
    	return ret;
    }
    /**
     * Sets the WhiteboxPluginHost to which the plugin tool is tied. This is the class
     * that the plugin will send all feedback messages, progress updates, and return objects.
     * @param host The WhiteboxPluginHost that called the plugin tool.
     */  
    @Override
    public void setPluginHost(WhiteboxPluginHost host) {
        myHost = host;
    }
    /**
     * Used to communicate feedback pop-up messages between a plugin tool and the main Whitebox user-interface.
     * @param feedback String containing the text to display.
     */
    private void showFeedback(String message) {
        if (myHost != null) {
            myHost.showFeedback(message);
        } else {
            System.out.println(message);
        }
    }
    /**
     * Used to communicate a return object from a plugin tool to the main Whitebox user-interface.
     * @return Object, such as an output WhiteboxRaster.
     */
    private void returnData(Object ret) {
        if (myHost != null) {
            myHost.returnData(ret);
        }
    }

    private int previousProgress = 0;
    private String previousProgressLabel = "";
    /**
     * Used to communicate a progress update between a plugin tool and the main Whitebox user interface.
     * @param progressLabel A String to use for the progress label.
     * @param progress Float containing the progress value (between 0 and 100).
     */
    private void updateProgress(String progressLabel, int progress) {
        if (myHost != null && ((progress != previousProgress) || 
                (!progressLabel.equals(previousProgressLabel)))) {
            myHost.updateProgress(progressLabel, progress);
        }
        previousProgress = progress;
        previousProgressLabel = progressLabel;
    }
    /**
     * Used to communicate a progress update between a plugin tool and the main Whitebox user interface.
     * @param progress Float containing the progress value (between 0 and 100).
     */
    private void updateProgress(int progress) {
        if (myHost != null && progress != previousProgress) {
            myHost.updateProgress(progress);
        }
        previousProgress = progress;
    }
    /**
     * Sets the arguments (parameters) used by the plugin.
     * @param args 
     */
    @Override
    public void setArgs(String[] args) {
        this.args = args.clone();
    }
    
    private boolean cancelOp = false;
    /**
     * Used to communicate a cancel operation from the Whitebox GUI.
     * @param cancel Set to true if the plugin should be canceled.
     */
    @Override
    public void setCancelOp(boolean cancel) {
        cancelOp = cancel;
    }
    
    private void cancelOperation() {
        showFeedback("Operation cancelled.");
        updateProgress("Progress: ", 0);
    }
    
    private boolean amIActive = false;
    /**
     * Used by the Whitebox GUI to tell if this plugin is still running.
     * @return a boolean describing whether or not the plugin is actively being used.
     */
    @Override
    public boolean isActive() {
        return amIActive;
    }

    @Override
    public void run() {
        amIActive = true;
                
        String sourceHeader = null;
        String outputHeader = null;
        String backLinkHeader = null;
        WhiteboxRaster sourceImage;
        WhiteboxRaster output;
        WhiteboxRaster backLink;
        int cols, rows;
        double z, flowDir;
        float progress = 0;
        int[] dX = new int[]{1, 1, 1, 0, -1, -1, -1, 0};
        int[] dY = new int[]{-1, 0, 1, 1, 1, 0, -1, -1};
        double lnOf2 = 0.693147180559945;
        double gridRes;
        int col, row, a;
        int c;
        int x, y, i;
        boolean flag = false;
        
    
        if (args.length <= 0) {
            showFeedback("Plugin parameters have not been set.");
            return;
        }
        
        for (i = 0; i < args.length; i++) {
            if (i == 0) {
                sourceHeader = args[i];
            } else if (i == 1) {
                backLinkHeader = args[i];
            } else if (i == 2) {
                outputHeader = args[i];
            }
        }

        // check to see that the inputHeader and outputHeader are not null.
        if ((sourceHeader == null) || (backLinkHeader == null) || (outputHeader == null)) {
            showFeedback("One or more of the input parameters have not been set properly.");
            return;
        }
        
        try {
            sourceImage = new WhiteboxRaster(sourceHeader, "r");
            rows = sourceImage.getNumberRows();
            cols = sourceImage.getNumberColumns();
            double noData = sourceImage.getNoDataValue();
            gridRes = (sourceImage.getCellSizeX() + sourceImage.getCellSizeY()) / 2;

            backLink = new WhiteboxRaster(backLinkHeader, "r");
            if (backLink.getNumberColumns() != cols || 
                    backLink.getNumberRows() != rows) {
                showFeedback("Input images must have the same dimensions");
                return;
            }

            output = new WhiteboxRaster(outputHeader, "rw", sourceHeader, WhiteboxRaster.DataType.FLOAT, noData);
            output.setPreferredPalette(sourceImage.getPreferredPalette());
            
            updateProgress("Loop 1 of 2:", 0);
            double[] data;
            for (row = 0; row < rows; row++) {
                data = sourceImage.getRowValues(row);
                for (col = 0; col < cols; col++) {
                    if (data[col] > 0) {
                        output.setValue(row, col, data[col]);
                    }
                }
                if (cancelOp) {
                    cancelOperation();
                    return;
                }
                progress = (float)(100f * row / (rows - 1));
                updateProgress("Loop 1 of 2:", (int) progress);
            }

            updateProgress("Loop 2 of 2:", 0);
            for (row = 0; row < rows; row++) {
                for (col = 0; col < cols; col++) {
                    if (backLink.getValue(row, col) != noData &&
                            output.getValue(row, col) == noData) {
                        x = col;
                        y = row;
                        z = noData;
                        flag = true;
                        do {
                            flowDir = backLink.getValue(y, x);
                            if (flowDir > 0) {
                                //move x and y accordingly
                                c = (int)(Math.log(flowDir) / lnOf2);
                                x += dX[c];
                                y += dY[c];
                                if (output.getValue(y, x) > 0) {
                                    z = output.getValue(y, x);
                                    flag = false; //stop the flowpath traverse
                                }
                            } else {
                                z = output.getValue(y, x);
                                flag = false; //stop the flowpath traverse
                            }
                        } while (flag);

                        x = col;
                        y = row;
                        flag = true;
                        do {
                            output.setValue(y, x, z);
                            flowDir = backLink.getValue(y, x);
                            if (flowDir > 0) {
                                //move x and y accordingly
                                c = (int)(Math.log(flowDir) / lnOf2);
                                x += dX[c];
                                y += dY[c];
                                if (output.getValue(y, x) > 0) {
                                    flag = false; //stop the flowpath traverse
                                }
                            } else {
                                flag = false; //stop the flowpath traverse
                            }
                        } while (flag);
                    }
                }
                if (cancelOp) {
                    cancelOperation();
                    return;
                }
                progress = (float)(100f * row / (rows - 1));
                updateProgress("Loop 2 of 2:", (int) progress);
            }
                    
            
            output.addMetadataEntry("Created by the "
                    + getDescriptiveName() + " tool.");
            output.addMetadataEntry("Created on " + new Date());
            
            sourceImage.close();
            backLink.close();
            output.close();
            
            // returning a header file string displays the image.
            returnData(outputHeader);

        } catch (OutOfMemoryError oe) {
            myHost.showFeedback("An out-of-memory error has occurred during operation.");
        } catch (Exception e) {
            myHost.showFeedback("An error has occurred during operation. See log file for details.");
            myHost.logException("Error in " + getDescriptiveName(), e);
        } finally {
            updateProgress("Progress: ", 0);
            // tells the main application that this process is completed.
            amIActive = false;
            myHost.pluginComplete();
        }
    }
}