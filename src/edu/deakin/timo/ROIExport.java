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
import java.io.*;


/*
	Save the polygon coordinates of the current ROI
 */

public class ROIExport implements PlugIn {
	
	/**Implement the PlugIn interface*/
    public void run(String arg) {
        ImagePlus imp = WindowManager.getCurrentImage();

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
		/*Check that an image was open*/
		if (imp == null) {
            IJ.noImage();
            return;
        }
		
		Roi tempRoi = imp.getRoi();
		if (tempRoi != null){
			Polygon polygonToSave = tempRoi.getPolygon();
			if (polygonToSave != null){
				//Generate a savename
				double[] means = new double[2];
				Calibration calib = imp.getCalibration();
				double heightScale = calib.pixelHeight;
				double widthScale = calib.pixelWidth;
				for (int i = 0; i<polygonToSave.npoints;++i){
					means[0] += ((double)polygonToSave.xpoints[i]);//*widthScale;
					means[1] += ((double)polygonToSave.ypoints[i]);//*heightScale;
				}
				means[0]/=(double)polygonToSave.npoints;
				means[1]/=(double)polygonToSave.npoints;
				String settings[] = rSettings.getSettings();
				rSettings.saveSettings();	//Save ROISettings settings
				String saveName = settings[2]+"/"+settings[4]+"/"+imp.getShortTitle()+"/"+imp.getStack().getShortSliceLabel(imp.getCurrentSlice())
				+"_"+String.format("%04d",(int) means[1])
				+"_"+String.format("%04d",(int) means[0])
				+".txt";
				try{
					File saveFile = new File(saveName);
					saveFile.getParentFile().mkdirs();	//Create folders for the file, if they don't exist
					BufferedWriter bw =  new BufferedWriter(new FileWriter(saveFile));
					bw.write("No\tX [pixels]\tY  [pixels]\tX [mm]\tY [mm]\n");
					//Write the coordinates
					for (int i = 0; i<polygonToSave.npoints;++i){
						bw.write(String.format(Locale.US,"%d\t%d\t%d\t%f\t%f\n",i
						,polygonToSave.xpoints[i],polygonToSave.ypoints[i]
						,((double)polygonToSave.xpoints[i])*widthScale,((double)polygonToSave.ypoints[i])*heightScale));
					}
					bw.close();
				} catch (Exception err) {
					IJ.log("Couldn't save the polygon");
				}
			}
		}
		//subRegions.saveResults(savePath,imp,visualIP,rMan);	//Save the results to disk		
	}
	
	
}
