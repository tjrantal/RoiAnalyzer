/*
 Image/J Plugins
 Copyright (C) 2012 Timo Rantalainen
 Author's email: tjrantal at gmail dot com
 The code is licenced under GPL 2.0 or newer
 */
package edu.deakin.timo;

import ij.*;
import ij.plugin.*;
import ij.gui.*;

import edu.deakin.timo.utils.*;
/*Choosing and saving a file*/
import java.awt.Frame;
/*
 Settings for Roi tools
 */

public class SetROISettingsPlugin implements PlugIn {

	/**Implement the PlugIn interface*/
    public void run(String arg) {
		GenericDialog gd = new GenericDialog("Set RoiSettings");
		gd.addStringField("ROI to read :","");
		gd.addStringField("Stack path :","");
		gd.showDialog();
		if (gd.wasCanceled()) return;
		//Get settings from the ROISettings
		/*Get save path*/
		Frame[] frames = Frame.getFrames();
		int fr = 0;
		while (fr < frames.length && frames[fr].getTitle() != "ROISettings"){
			++fr;
		}
		//If ROISettings have not been opened, create new
		ROISettings rSettings;
		if (fr>= frames.length || frames[fr].getTitle() != "ROISettings"){
			rSettings = new ROISettings();
		}else{
			if (frames[fr] instanceof edu.deakin.timo.utils.ROISettings){
				rSettings = (ROISettings) frames[fr];
			}else{
				rSettings = new ROISettings();	//Should never get here!
			}
		}
		rSettings.setVisible(true);
		rSettings.setTextField("ROI to read",gd.getNextString());
		rSettings.setTextField("Stack path",gd.getNextString());
		String settings[] = rSettings.getSettings();
		rSettings.saveSettings();	//Save ROISettings settings
	
	}
}
