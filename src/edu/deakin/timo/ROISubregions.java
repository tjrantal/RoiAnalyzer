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
	RoiManager rMan;


	/**Implement the PlugIn interface*/
    public void run(String arg) {
		imw = WindowManager.getCurrentWindow();
		canvas = imw.getCanvas();
        imp = WindowManager.getCurrentImage();
		//IJ.log(imp.getStack().getShortSliceLabel(imp.getSlice()));

		int[] subDivisions = new int[]{5,1};	/*Get this from a menu, width wise divisions, height wise*/
		
		/*Check that an image was open*/
		if (imp == null) {
            IJ.noImage();
            return;
        }
		/*Check that the image is 16 bit gray image*/
		if (imp.getType() != ImagePlus.GRAY16){
			IJ.error("IJGrower expects 16-bit greyscale data, e.g. DICOM-image");
			return;
		}
		
		
		
		/*Get image size and stack depth*/
		int width = imp.getWidth();
		int height = imp.getHeight();
		depth = imp.getStackSize();
		int currentSlice =imp.getSlice();
		
		/**Pop up Roi Manager*/
		if (RoiManager.getInstance() == null){
			rMan = new RoiManager();
		}else{
			rMan = RoiManager.getInstance();
		}
		/**Add the ROI to roimanager*/
		//Go through 8 consecutive slices
		ImagePlus visualIP		= null;
		SubRegions subRegions	= null;
		for (int i = 0; i<8;++i){
			imp.setSlice(currentSlice);
			rMan.add(imp,imp.getRoi(),imp.getSlice());
			//IJ.log("stack depth "+depth+" current slice "+currentSlice);
			/**Get the visualization stack*/
			visualIP = getVisualizationStack(imp);
			
			/**Get ROI mask for the current ROI*/
			byte[] roiMask = getRoiMask(imp);
			/**Get the mask pixel coordinates, calculate the rotation angle for the ROI, and get the rotated coordinates*/
			PixelCoordinates pixelCoordinates = new PixelCoordinates(roiMask,width,height);
			
			subRegions = new SubRegions(imp,pixelCoordinates,subDivisions);
			subRegions.printResults();	//Print the results to a TextPanel
			/*Color the subregions to visualize the division*/
			visualizeRegions(visualIP,subRegions,subDivisions,pixelCoordinates);
			/*Re-activate the original stack*/
			WindowManager.toFront(imw);
			WindowManager.setWindow(imw); 
			WindowManager.setCurrentWindow(imw); 
			++currentSlice;
		}
		if (visualIP != null && subRegions != null){
			subRegions.saveResults(savePath,imp,visualIP,rMan);	//Save the results to disk		
		}
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
			switch (regionIndices[0][i]+regionIndices[1][i]*subDivisions[0]){
				case 0:
					rgb[1] = 0;
					break;
				case 1:
					rgb[0] = 0;
					rgb[1] = 0;
					break;
				case 2:
					rgb[0] = 0;
					rgb[2] = 0;
					break;
				case 3:
					rgb[1] = 0;
					rgb[2] = 0;
					break;
				case 4:
					rgb[2] = 0;
					break;
			}
			visualIP.getProcessor().setColor(new Color(rgb[2],rgb[1],rgb[0]));
			visualIP.getProcessor().drawPixel((int)pc.coordinates[i][0],(int)pc.coordinates[i][1]);
		}
		visualIP.show();
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
		Rectangle rect = ijROI.getBoundingRect();
		
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
		Duplicate the current stack, or if it has already been duplicated get the duplicate stack, and set the slice to the ROI slice
		@param imp the stack with the current ROI
	*/
	private ImagePlus getVisualizationStack(ImagePlus imp){
		/**Duplicate the image stack to be used for visualization*/
		int width = imp.getWidth();
		int height = imp.getHeight();
		int depth = imp.getStackSize();
		int currentSlice = imp.getSlice();
		String stackName = imp.getTitle();
		String visualName = stackName+" visualization";
		Window vsw = WindowManager.getWindow(visualName);
		ImagePlus visualIP;
		if (vsw == null){
			//IJ.log("Didn't find visual stack");
			/*Create a visualization stack, duplicate the original stack*/
			ImageStack visualizationStack = new ImageStack(width,height);
			//IJ.log("Stack created");
			for (int i = 1;i<=depth;++i){
				imp.setSlice(i);
				//IJ.log("Slice changed");
				short[] slicePixels = Arrays.copyOf((short[]) imp.getProcessor().getPixels(),((short[])imp.getProcessor().getPixels()).length);
				//IJ.log("Copied pixels "+slicePixels.length);
				//visualizationStack.addSlice(imp.duplicate().getProcessor());
				visualizationStack.addSlice(null,slicePixels);
				//IJ.log("Added slice "+i);
			}
			//IJ.log("Set imp to currentSlice");
			imp.setSlice(currentSlice);
			
			//visualizationStack =imp.getImageStack().duplicate();
			//IJ.log("Duplicated stack");
			//visualIP = imp.duplicate(); //new ImagePlus(visualName,visualizationStack);
			visualIP = new ImagePlus(visualName,visualizationStack);
			//visualIP.setTitle(visualName);
			//IJ.log("Got imagePlus for vstack");
			new ImageConverter(visualIP).convertToRGB();	//Convert the stack to RGB for visualization
			
			/**Define save path*/
			preferences = Preferences.userRoot().node(this.getClass().getName());
			try{
				savePath = preferences.get(keySP,new File( "." ).getCanonicalPath()); /*Use current working directory as
			default*/
			}catch (IOException ex){
				System.out.println(ex);
				savePath = ".";
			}
			/*Instantiate fileChooser*/
			fileChooser = new JFileChooser(savePath);							/*Implements the file chooser*/
			//fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);	/*Limit to choosing files*/
			fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			int returnVal = fileChooser.showOpenDialog(WindowManager.getActiveWindow());
             if (returnVal == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
				try{
					if (file.isDirectory()){
						preferences.put(keySP,file.getCanonicalPath());
					}else{
						preferences.put(keySP,(new File(file.getParent())).getCanonicalPath());
					}
					System.out.println("Save path set "+preferences.get(keySP,"."));
				}catch (IOException ex){
					System.out.println(ex);
					savePath = ".";
				}
            } else {
                System.out.println("Cancelled file dialog");
            }
		}else{
			//IJ.log("Found visual stack");
			WindowManager.setWindow(vsw);
			if (vsw instanceof ImageWindow){
				//IJ.log("visual stack instanceof ImageWindow");
				WindowManager.setCurrentWindow((ImageWindow) vsw);
			}
			
			//WindowManager.setCurrentWindow(WindowManager.getActiveWindow());
			visualIP =   WindowManager.getCurrentImage();
			//visualizationStack = visualIP.getImageStack();
			/**Define save path, read the value from preferences*/
			preferences = Preferences.userRoot().node(this.getClass().getName());
			try{
				savePath = preferences.get(keySP,new File( "." ).getCanonicalPath()); /*Use current working directory as
			default*/
			}catch (IOException ex){
				System.out.println(ex);
				savePath = ".";
			}
		}
		visualIP.setSlice(currentSlice);
		return visualIP;
	}
}
