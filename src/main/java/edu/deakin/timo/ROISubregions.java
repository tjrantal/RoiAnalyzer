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
import java.util.ArrayList;
import java.util.Collections;		//Sorting

//Ellipse fit
import timo.deakin.ellipsefit.PolyFit;
import timo.deakin.ellipsefit.EllipseFit;
import timo.deakin.ellipsefit.NormalPoint;
import java.awt.Polygon;
import java.awt.Color;
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
	ArrayList<Coordinate> coordinates;

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
		String[] settings = rSettings.getSettings();
		rSettings.saveSettings();	//Save ROISettings settings
		
		int[] subDivisions = new int[]{Integer.parseInt(settings[1]),Integer.parseInt(settings[0])};	/*Get this from a menu, width wise divisions 
(columns), height wise (rows)*/
		//System.out.println("columns "+subDivisions[0]+" rows "+subDivisions[1]);
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
		
		
		/**Get ROI mask for the current ROI*/
		
		
		coordinates = new ArrayList<Coordinate>();
		byte[] roiMask = getRoiMask(imp);
		/**Do not do subregions, if using ellipse fit, settings 11 = 1*/
		int fitOrder = Integer.parseInt(settings[11]);
		if (fitOrder == 0){
			//DO MASK HERE
			doMask(imp, imp, roiMask, width, height, subDivisions, settings);
		}else{
			//USING Polynomial FIT!!!
			//Pop digitized coordinates into coordinates
			Roi temp = imp.getRoi();
			ArrayList<Coordinate> digitizedCoordinates = new ArrayList<Coordinate>();
			if (temp instanceof PolygonRoi){
				FloatPolygon tempP = ((PolygonRoi) temp).getFloatPolygon();
				digitizedCoordinates.clear();
				for (int i = 0; i<tempP.npoints;++i){
					digitizedCoordinates.add(new Coordinate(tempP.xpoints[i],tempP.ypoints[i]));
				}
				
				
			}
			
			//Fit an n:th order polynomial with the least squares method
			
			//IJ.log("Using ellipse fit "+coordinates.size());
			//EllipseFit ef = new EllipseFit(coordinates,tolerance);
			PolyFit pf = new PolyFit(coordinates,fitOrder);
			double[] coeffs = pf.getCoeffs();	//Get fit
			double[] dCoeffs = pf.getDerivCoeffs();	//Get derivative coefficients (-1/dCoeffs = normals)
			
			//DEBUGGING
			/*
			String coeffsString = "";
			String dCoeffsString = "";
			for (int i = 0; i< coeffs.length;++i){
				coeffsString+=String.format(" C%d %.2f",i,coeffs[i]);
				if (i<dCoeffs.length){
					dCoeffsString+=String.format(" dC%d %.2f",i,dCoeffs[i]);
				}
			}
			IJ.log(coeffsString);
			IJ.log(dCoeffsString);
			*/
			
			//Calculate distance from fit to the point for all pixels -> use to determine bounding box
			NormalPoint[] distances = new NormalPoint[coordinates.size()];
			for (int i = 0; i<coordinates.size();++i){
				distances[i] = pf.getFitPoint(coordinates.get(i),true);
			}
			//Get max distance
			Arrays.sort(distances);
			int maxDistance = (int) Math.ceil(distances[distances.length-1].distance)+2; //get max distance from fit + add a margin of two pixels
			
			//Sort coordinates Y-coordinate to get Y-range
			Collections.sort(coordinates);
			int minY = ((int) Math.floor(coordinates.get(0).y)) - maxDistance;	//Include the additional space
			int maxY = ((int) Math.floor(coordinates.get(coordinates.size()-1).y)) + maxDistance;	//Include the additional space
			
			//COVER THE DISTANCE FROM minY to maxY in 1 pixel increments along the fit (line integral), have to solve iteratively
			//Save the step coordinates in flattenCoordinates -> step through these, and interpolate data along the normal at the step coordinates to flatten the ROI
			double yVal = minY;
			double xVal =  getFitVal(coeffs,yVal);
			double increment,currentTarget;
			double sum = 0,prevSum=1d;
			
			//IJ.log(String.format("Prior X %.2f Y %.2f",getFitVal(coeffs,yVal),yVal));
			currentTarget = 1d;
			double prevX,prevY;
			//int steps = 0;
			prevY = yVal;
			prevX = xVal;
			ArrayList<Coordinate> flattenCoordinates = new ArrayList<Coordinate>();
			flattenCoordinates.add(new Coordinate(xVal,yVal));
			while (yVal <=maxY-1d){
				increment = 0.1;
				while (sum<currentTarget){
					yVal+=increment;
					xVal = getFitVal(coeffs,yVal);
					if (increment > 0){
						sum+=Math.sqrt(Math.pow(increment,2d)+Math.pow(prevX-xVal,2d));
					}else{
						sum-=Math.sqrt(Math.pow(increment,2d)+Math.pow(prevX-xVal,2d));
					}
					prevY = yVal;
					prevX = xVal;
					if (currentTarget-sum < 0.2d){
						increment= 0.01;
						if (currentTarget-sum < 0.1d){
							increment= 0.001;
						}
					}
					
				}
				//Should have advanced one pixel here, add the current coordinate
				flattenCoordinates.add(new Coordinate(xVal,yVal));
				//IJ.log(String.format("Step %d X %.2f Y %.2f",steps,getFitVal(coeffs,yVal),yVal));
				//++steps;
				currentTarget += 1d;
			}
			
			//Loop through flattenCoordinates and interpolate pixels
			int flatWidth = flattenCoordinates.size();
			int flatHeight = (maxDistance*2+1);
			short[] pixels = new short[flatWidth*flatHeight];	//Pixels for flattened image
			byte[] maskPixels = new byte[flatWidth*flatHeight];	//Pixels for flattened mask
			double xTangentSlope;
			ImageProcessor ip = imp.getProcessor();
			//Create roi mask processor
			ByteProcessor bpro = new ByteProcessor(width,height);
			bpro.setPixels(roiMask);			
			for (int i = 0; i<flattenCoordinates.size();++i){
				xTangentSlope= getFitVal(dCoeffs,flattenCoordinates.get(i).y);
				double[] tangentUnit = PolyFit.normalise(new double[]{xTangentSlope,1d});	//Get unit tangent vector
				double[] unitNormal = new double[]{-tangentUnit[1],tangentUnit[0]};  //Rotate the tangent clockwise by 90 deg (y points down!!!), unit normal
				//Get point along the unitNormal
				double cX = flattenCoordinates.get(i).x;
				double cY = flattenCoordinates.get(i).y;
				for (int j = 0;j< flatHeight; ++j){
					pixels[i+j*flatWidth] = (short) ip.getInterpolatedPixel(cX+unitNormal[0]*(j-((double) maxDistance)), cY+unitNormal[1]*(j-((double) maxDistance)));
					//Anything above 0.5 = 1, below 0;
					maskPixels[i+j*flatWidth] = bpro.getInterpolatedPixel(cX+unitNormal[0]*(j-((double) maxDistance)), cY+unitNormal[1]*(j-((double) maxDistance))) <0.5 ? (byte) 0 : (byte) 1;
				}
			}
			ShortProcessor fpro = new ShortProcessor(flatWidth,flatHeight);
			fpro.setPixels(pixels);
			ImagePlus flattenedIP = new ImagePlus(imp.getTitle()+" flattened",fpro);
			flattenedIP.copyScale(imp);
			//flattenedIP.show();
			
			/*
			//DEBUGGING
			ByteProcessor bfpro = new ByteProcessor(flatWidth,flatHeight);
			bfpro.setPixels(maskPixels);			
			ImagePlus maskIP = new ImagePlus(imp.getTitle()+" mask",bfpro);
			maskIP.copyScale(imp);	//Get the scale of the original image
			maskIP.setDisplayRange(0,1);			
			*/
			//maskIP.show();
			
			//Feed maskIP and flattenedIP into PixelCoordinates and SubRegions
			//DO MASK HERE
			String[] tempSettings = (String[]) Arrays.copyOf(settings,settings.length);
			tempSettings[8] = "4";	//Always set rotation to 0
			doMask(flattenedIP,imp, maskPixels, flatWidth, flatHeight, subDivisions, tempSettings);
		}
		
		/*Re-activate the original stack*/
		imw.toFront();
		imw.requestFocus();
		WindowManager.toFront(imw);
		WindowManager.setWindow(imw); 
		WindowManager.setCurrentWindow(imw); 

	}
	
	//Do PixelCoordinates and SubRegions
	private void doMask(ImagePlus imageIn,ImagePlus origIn, byte[] maskIn, int widthIn, int heightIn, int[] subDivisionsIn, String[] settingsIn){
		/**Get the mask pixel coordinates, calculate the rotation angle for the ROI, and get the rotated coordinates*/
		PixelCoordinates pixelCoordinates = new PixelCoordinates(maskIn,widthIn,heightIn,Integer.parseInt(settingsIn[8]));
		SubRegions subRegions = new SubRegions(imageIn,pixelCoordinates,subDivisionsIn);
		subRegions.printResults(settingsIn,origIn);	//Print the results to a TextPanel
		/**Get the visualization stack*/
		if (Double.parseDouble(settingsIn[5]) >= 1){
			ImagePlus visualIP		= null;
			visualIP = getVisualizationSlice(imageIn);
			/*Color the subregions to visualize the division*/
			visualizeRegions(visualIP,subRegions,subDivisionsIn,pixelCoordinates);
		}
	}
	
	private double getFitVal(double[] c,double x){
		double val = 0;
		for (int d = 0; d<c.length;++d){
			val+=c[d]*Math.pow(x,(double) d);
		}
		return val;
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
						coordinates.add(new Coordinate(i,j));	//Add ROI coordinates into coordinates ArrayList
					}
				}
			}
		}else{
			/*rectangular ROI, use bounding rectangle*/
			for (int j = rect.y;j< rect.y+rect.height;++j){
				for (int i = rect.x; i < rect.x+rect.width;++i){
					roiMask[i+j*width] =1;	/*In ROI = 1, out = 0*/
					coordinates.add(new Coordinate(i,j));	//Add ROI coordinates into coordinates ArrayList
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
