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

public class ROISubregions implements PlugIn {
	
	ImageWindow imw;
	ImageCanvas canvas;
	ImagePlus imp;
	int currentSlice = -1;
	int depth = -1;
	private JFileChooser fileChooser;		/**Selecting the file to save to*/
	private Preferences preferences;		/**Saving the default file path*/
	private final String keySP = "SP";
	private String savePath;

	/**Implement the PlugIn interface*/
    public void run(String arg) {
		imw = WindowManager.getCurrentWindow();
		canvas = imw.getCanvas();
        imp = WindowManager.getCurrentImage();
		
		
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
		String settings[] = rSettings.getSettings();
		rSettings.saveSettings();	//Save ROISettings settings
		
		int[] subDivisions = new int[]{Integer.parseInt(settings[1]),Integer.parseInt(settings[0])};	/*Get this from a menu, width wise divisions 
(columns), height wise (rows)*/
		System.out.println("columns "+subDivisions[0]+" rows "+subDivisions[1]);
		/*Check that an image was open*/
		if (imp == null) {
            IJ.noImage();
            return;
        }
		/*Check that the image is 16 bit gray image*/
		if (imp.getType() != ImagePlus.GRAY16){
			//Convert the image to 16bit do not warn the user
			new ImageConverter(imp).convertToGray16();
			
			
			//IJ.error("IJGrower expects 16-bit greyscale data, e.g. DICOM-image");
			//return;
		}
		
		/*Get image size and stack depth*/
		int width = imp.getWidth();
		int height = imp.getHeight();
		depth = imp.getStackSize();
		int currentSlice =imp.getSlice();
		
		SubRegions subRegions	= null;
		
		/**Get ROI mask for the current ROI*/
		byte[] roiMask = getRoiMask(imp);
		/**Get the mask pixel coordinates, calculate the rotation angle for the ROI, and get the rotated coordinates*/
		PixelCoordinates pixelCoordinates = new PixelCoordinates(roiMask,width,height,Integer.parseInt(settings[8]));
		
		subRegions = new SubRegions(imp,pixelCoordinates,subDivisions);
		subRegions.printResults(settings,imp);	//Print the results to a TextPanel
		/**Get the visualization stack*/
		if (Double.parseDouble(settings[5]) >= 1){
			ImagePlus visualIP		= null;
			visualIP = getVisualizationSlice(imp);
			/*Color the subregions to visualize the division*/
			visualizeRegions(visualIP,subRegions,subDivisions,pixelCoordinates);
		}
		
		/*Re-activate the original stack*/
		imw.toFront();
		imw.requestFocus();
		WindowManager.toFront(imw);
		WindowManager.setWindow(imw); 
		WindowManager.setCurrentWindow(imw); 

	}
	
	private void visualizeRegions(ImagePlus visualIP,SubRegions subRegions,int[] subDivisions,PixelCoordinates pc){
		int[] rgb = new int[3];
		int[][] regionIndices = subRegions.getSubregions();
		int value;
		for (int i = 0; i < pc.coordinates.length;++i) {
			value = visualIP.getProcessor().getPixel((int)pc.coordinates[i][0],(int)pc.coordinates[i][1]);
			for (int c = 0; c<3;++c){
				rgb[c] = (value >>(c*8))& 0XFF;
			}
			//Come up with a proper colour palette selection at some point...
			int addBright = 25;
			rgb[0] = (regionIndices[0][i] % 3 == 0) ? (rgb[0]+addBright < 255 ? rgb[0]+addBright:255) : (int) (((double) rgb[0])*((double) ((regionIndices[0][i]+regionIndices[1][i]*subDivisions[0])))/((double)(subDivisions[0]*subDivisions[1])));
			rgb[1] = (regionIndices[1][i] % 3 == 1) ? (rgb[1]+addBright < 255 ? rgb[1]+addBright:255) : (int) (((double) rgb[1])*((double) ((subDivisions[0]*subDivisions[1])-(regionIndices[0][i]+regionIndices[1][i]*subDivisions[0])))/((double)(subDivisions[0]*subDivisions[1])));
			rgb[2] = ((regionIndices[0][i]*subDivisions[1]+regionIndices[1][i]) % 3 == 2) ? (rgb[2]+addBright < 255 ? rgb[2]+addBright:255) : (int) (((double) rgb[2])*((double) ((subDivisions[0]*subDivisions[1])-(regionIndices[0][i]*subDivisions[1]+regionIndices[1][i])))/((double)(subDivisions[0]*subDivisions[1])));
			//rgb[1] = (int) ((double) rgb[1])*(((double)(subDivisions[0]*subdivisions[1]))-((double) (regionIndices[0][i]+regionIndices[1][i]*subDivisions[0])))/(((double)(subDivisions[0]*subdivisions[1]);

			visualIP.getProcessor().setColor(new Color(rgb[2],rgb[1],rgb[0]));
			visualIP.getProcessor().drawPixel((int)pc.coordinates[i][0],(int)pc.coordinates[i][1]);
		}
		visualIP.show();
		//Set position on screen. Get image coordinates -> position next to that
		visualIP.getWindow().setLocation(imp.getWindow().getLocationOnScreen().x+imp.getWindow().getSize().width+5,imp.getWindow().getLocationOnScreen().y);
		visualIP.repaintWindow();
	}
	
	
	
	/**
		Get a byte mask for the ROI of the current imp. 
		@param imp the imagePlus of the current ROI
		@return roiMask a byte array mask of the current image ROI; 1 = in ROI, 0 = out
	*/
	private byte[] getRoiMask(ImagePlus imp){
		/*Determine mask for ROI-specific calculations, set mask to 1, if it belongs to the ROI, 0 otherwise*/
		int width = imp.getWidth();
		int height = imp.getHeight();
		int depth = imp.getStackSize();
		byte[] roiMask = new byte[width*height];	/*Automatically initialized to zero*/
		Roi ijROI = imp.getRoi();	//Get the current ROI
		Rectangle rect = ijROI.getBounds();
		
		/*Create ROI mask*/
		if (imp.getMask() != null){
			/*irregular roi, use Roi and bounding rectangle*/
			byte[] tempMask = (byte[]) imp.getMask().getPixels();	/*Out of mask = 0*/
			for (int j = rect.y;j< rect.y+rect.height;++j){
				for (int i = rect.x; i < rect.x+rect.width;++i){
					if (tempMask[i-rect.x+(j-rect.y)*rect.width] !=0){
						roiMask[i+j*width] =1;	/*In ROI = 1, out = 0*/
					}
				}
			}
		}else{
			/*rectangular ROI, use bounding rectangle*/
			for (int j = rect.y;j< rect.y+rect.height;++j){
				for (int i = rect.x; i < rect.x+rect.width;++i){
					roiMask[i+j*width] =1;	/*In ROI = 1, out = 0*/
				}
			}
		}
		return roiMask;
	}

	/**
		Duplicate the current image, and set color model to RGB
		@param imp the stack with the current ROI
	*/
	private ImagePlus getVisualizationSlice(ImagePlus imp){
		/**Duplicate the image stack to be used for visualization*/
		int width = imp.getWidth();
		int height = imp.getHeight();
		String stackName = imp.getTitle();
		String visualName = stackName+" visualization";
		ImagePlus visualIP;
		short[] slicePixels = Arrays.copyOf((short[]) imp.getProcessor().getPixels(),((short[])imp.getProcessor().getPixels()).length);
		ShortProcessor tp = new ShortProcessor(width,height);
		tp.setPixels(slicePixels);
		visualIP = new ImagePlus(visualName,tp);
		new ImageConverter(visualIP).convertToRGB();	//Convert the stack to RGB for visualization
		
		return visualIP;
	}
}
