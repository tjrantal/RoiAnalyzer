/*
 Image/J Plugins
 Copyright (C) 2012 Timo Rantalainen
 Author's email: tjrantal at gmail dot com
 The code is licenced under GPL 2.0 or newer
 */
package edu.deakin.timo;

import ij.*;
import ij.plugin.*;
import ij.plugin.frame.*;
import ij.measure.*;
import ij.gui.*;	
import ij.process.*;
import ij.io.*;
import ij.text.TextPanel;
import java.util.*;
import java.awt.*;
import java.awt.event.*;	
import java.text.*;
import edu.deakin.timo.utils.*;
/*Choosing and saving a file*/
import javax.swing.SwingUtilities;
import javax.swing.JFileChooser;
import java.util.prefs.Preferences;		/*Saving the file save path -> no need to re-browse...*/
import java.io.File;
import java.io.IOException;

/*
 Performs connected region growing. User is asked to provide the seed area points.
 Result is displayed as a binary image. Works with 3D images stack.
 */

public class ROIExport implements PlugIn {
	
	ImageWindow imw;
	ImageCanvas canvas;
	ImagePlus imp;
	RoiManager rMan;

	/**Implement the PlugIn interface*/
    public void run(String arg) {
        imp = WindowManager.getCurrentImage();
	
		/*Check that an image was open*/
		if (imp == null) {
            IJ.noImage();
            return;
        }

		//subRegions.saveResults(savePath,imp,visualIP,rMan);	//Save the results to disk		
	}
	
	
}
