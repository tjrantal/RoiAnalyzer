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
 Performs connected region growing. User is asked to provide the seed area points.
 Result is displayed as a binary image. Works with 3D images stack.
 */

public class ROILoader implements PlugIn {
	ImageWindow imw;
	ImagePlus imp;
	private Preferences preferences;		/**Saving the default file path*/
	private final String keySP = "SP";
	private String savePath;

	/**Implement the PlugIn interface*/
    public void run(String arg) {
	
		//Get settings from the ROISettings
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
		String settings[] = rSettings.getSettings();	//Read settings from the ROISettings window
		rSettings.saveSettings();						//Save ROISettings settings
		//Settings loaded
		//Read ROI file
		String roiPath = settings[6];	//Roi file path
		String stackPath = settings[7];	//image stack path
		String fileSuffix = settings[12];	//image stack path
		//Get whether to erode ROI from settings
		int erodeByPixels = Integer.parseInt(settings[9]);
		int[][] roiCoordinates;
		try{
			//IJ.log("BufferedReader");
			BufferedReader br = new BufferedReader(new FileReader(roiPath));
			String line = br.readLine();	//Read the header line
			ArrayList<String[]> dataLines = new ArrayList<String[]>();
			line = br.readLine();
			while (line != null){
				dataLines.add(line.split("\t"));
				line = br.readLine();
			}
			roiCoordinates = new int[2][dataLines.size()];
			for (int i =0;i<dataLines.size();++i){
				roiCoordinates[0][i] = Integer.parseInt(dataLines.get(i)[1]);	//X-coordinate
				roiCoordinates[1][i] = Integer.parseInt(dataLines.get(i)[2]);	//Y-coordinate
				//IJ.log("X "+roiCoordinates[i][0]+" Y "+roiCoordinates[i][1]);
			}
		}catch (Exception err){
			System.out.println(err);
			return;
		}
		//Load image stack and change to correct slice
		//IJ.log(roiPath);
		String[] stackDescriptor;
		if ((roiPath.split("\\\\").length) > 1){
			stackDescriptor = roiPath.split("\\\\");
		}else{
			stackDescriptor = roiPath.split("/");
		}
		//IJ.log(stackDescriptor[stackDescriptor.length-1]);
		String[] imageNameSplit = stackDescriptor[stackDescriptor.length-1].split("_");
		String imageName = new String("");
		for (int i = 0;i<imageNameSplit.length-2;++i){
			imageName += imageNameSplit[i];
			if (i <imageNameSplit.length-3){
				imageName +="_";
			}
		}

		imageName +=fileSuffix;
		String fileToOpen = stackPath+"\\"+imageName;
		//IJ.log(fileToOpen);
		//IJ.open(stackPath+"/"+imageName);
		Opener opener = new Opener();
		//opener.open(stackPath+"/"+imageName);
		//String testOpen = "C:\\timo\\research\\BelavyQuittner2015\\stacks\\S001_Spin\\S001_Spin_01_01.dcm";
		imp = opener.openImage(fileToOpen);
		PolygonRoi roi = new PolygonRoi(roiCoordinates[0],roiCoordinates[1],roiCoordinates[0].length,Roi.POLYGON);//Roi.POLYLINE);
		
		//Erode ROI, if erodeByPixels is not zero
		if (erodeByPixels != 0){
			roi = (PolygonRoi) RoiEnlarger.enlarge(roi, (double) (-erodeByPixels));
		}

		imp.setRoi(roi);
		imp.show();
		//imp = new ImagePlus(stackPath+"/"+imageName);
		

	}
}
