/*
 Image/J Plugins
 Copyright (C) 2012 Timo Rantalainen
 Author's email: tjrantal at gmail dot com
 The code is licenced under GPL 2.0 or newer
 */
package edu.deakin.timo;

import ij.*;
import ij.plugin.*;

import edu.deakin.timo.utils.*;
/*Choosing and saving a file*/
import java.awt.Frame;
/*
 Settings for Roi tools
 */

public class ROISettingsPlugin implements PlugIn {

	/**Implement the PlugIn interface*/
    public void run(String arg) {
		//Pop-up ROISettings
		Frame[] frames = Frame.getFrames();
		int fr = 0;
		while (fr < frames.length && frames[fr].getTitle() != "ROISettings"){
			++fr;
		}
		//If ROISettings have not been opened, create new
		if (fr>= frames.length || frames[fr].getTitle() != "ROISettings"){
			new ROISettings();
		}
	}
}
