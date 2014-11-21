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
import edu.deakin.timo.utils.Utils;

/*
 Performs connected region growing. User is asked to provide the seed area points.
 Result is displayed as a binary image. Works with 3D images stack.
 */

public class ROISubregions implements PlugIn {
	
	ImageWindow imw;
	//ImageWindow visualizationStackWindow;
	ImageCanvas canvas;
	RoiManager rMan;
	ImagePlus imp;
	int currentSlice = -1;
	int depth = -1;


	
	/**Implement the PlugIn interface*/
    public void run(String arg) {
		imw = WindowManager.getCurrentWindow();
		canvas = imw.getCanvas();
        imp = WindowManager.getCurrentImage();
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
		int currentSlice = imp.getSlice();
		IJ.log("stack depth "+depth+" current slice "+currentSlice);
		/**Duplicate the image stack to be used for visualization*/
		String stackName = imp.getTitle();
		String visualName = stackName+" visualization";
		Window vsw = WindowManager.getWindow(visualName);
		
		ImagePlus visualIP;
		if (vsw == null){
			IJ.log("Didn't find visual stack");
			/*Create a visualization stack, duplicate the original stack*/
			
			ImageStack visualizationStack = new ImageStack(width,height);
			IJ.log("Stack created");
			for (int i = 1;i<=depth;++i){
				imp.setSlice(i);
				IJ.log("Slice changed");
				short[] slicePixels = Arrays.copyOf((short[]) imp.getProcessor().getPixels(),((short[])imp.getProcessor().getPixels()).length);
				IJ.log("Copied pixels "+slicePixels.length);
				//visualizationStack.addSlice(imp.duplicate().getProcessor());
				visualizationStack.addSlice(null,slicePixels);
				IJ.log("Added slice "+i);
			}
			IJ.log("Set imp to currentSlice");
			imp.setSlice(currentSlice);
			
			//visualizationStack =imp.getImageStack().duplicate();
			IJ.log("Duplicated stack");
			//visualIP = imp.duplicate(); //new ImagePlus(visualName,visualizationStack);
			visualIP = new ImagePlus(visualName,visualizationStack);
			//visualIP.setTitle(visualName);
			IJ.log("Got imagePlus for vstack");
			new ImageConverter(visualIP).convertToRGB();	//Convert the stack to RGB for visualization
			IJ.log("Converted to RGB");			
		}else{
			IJ.log("Found visual stack");
			WindowManager.setWindow(vsw);
			if (vsw instanceof ImageWindow){
				IJ.log("visual stack instanceof ImageWindow");
				WindowManager.setCurrentWindow((ImageWindow) vsw);
			}
			
			//WindowManager.setCurrentWindow(WindowManager.getActiveWindow());
			visualIP =   WindowManager.getCurrentImage();
			//visualizationStack = visualIP.getImageStack();
			
		}
		visualIP.setSlice(currentSlice);
		
		/**Get the current ROI*/
		Roi ijROI = imp.getRoi();
		Rectangle rect = ijROI.getBoundingRect();//imp.getProcessor().getRoi();
		
		/*Determine mask for ROI-specific calculations, set mask to 1, if it belongs to the ROI, 0 otherwise*/
		byte[] roiMask = new byte[width*height];	/*Automatically initialized to zero*/
		int roiPixels = 0;
		
		/*Create ROI mask*/
		if (imp.getMask() != null){
			/*irregular roi, use Roi and bounding rectangle*/
			byte[] tempMask = (byte[]) imp.getMask().getPixels();	/*Out of mask = 0*/
			for (int j = rect.y;j< rect.y+rect.height;++j){
				for (int i = rect.x; i < rect.x+rect.width;++i){
					if (tempMask[i-rect.x+(j-rect.y)*rect.width] !=0){
						roiMask[i+j*width] =1;	/*In ROI = 1, out = 0*/
						++roiPixels;
					}
				}
			}
		}else{
			/*rectangular ROI, use bounding rectangle*/
			for (int j = rect.y;j< rect.y+rect.height;++j){
				for (int i = rect.x; i < rect.x+rect.width;++i){
					roiMask[i+j*width] =1;	/*In ROI = 1, out = 0*/
					++roiPixels;
				}
			}
		}

		/*Do polynomial fit on the pixels*/
		double[][] fitArray = new double[roiPixels][2];
		double[] roiCentre = new double[2];	//Figure out roi centre to calculate X-, and Y-rotations
		//Get the coordinates
		roiPixels = 0;
		for (int j = 0;j< height;j++){
			for (int i = 0; i < width;i++){
				if (roiMask[i+j*width] == 1){
					fitArray[roiPixels][0] = i;	//x-coordinate
					fitArray[roiPixels][1] = j;	//y-coordinate
					roiCentre[0]+=(double) i;
					roiCentre[1]+=(double) j;
					++roiPixels;
				}
			}
		}
		roiCentre[0]/=(double)roiPixels;
		roiCentre[1]/=(double)roiPixels;
		
		double[] coeffs = Utils.polynomialFit(fitArray, 1);
		double angle = Math.atan(coeffs[1]);	/*The tangent of the rotation angle is coeffs[1]/1*/
		
		/*Rotate the ROI to calculate subregion division*/
		double[][] rotatedRoi = new double[fitArray.length][2];
		for (int i = 0;i<fitArray.length;++i){
			double[] temp = rotateVector(new double[]{fitArray[i][0],fitArray[i][1]},angle);
			rotatedRoi[i][0] = temp[0];
			rotatedRoi[i][1] = temp[1];
		}
		
		/*Calculate max width from the rotated ROI*/
		double[][] tempRotated = new double[2][rotatedRoi.length];
		for (int i = 0;i<rotatedRoi.length;++i){
			double[] temp = rotateVector(new double[]{fitArray[i][0],fitArray[i][1]},angle);
			tempRotated[0][i] = rotatedRoi[i][0];
			tempRotated[1][i] = rotatedRoi[i][1];
		}
		double[] minMax = new double[]{tempRotated[0][minInd(tempRotated[0])],tempRotated[0][maxInd(tempRotated[0])]};

		
		
		/**Assign pixels to subregions*/
		double roiWidth = minMax[1]-minMax[0]+1d;
		double regionWidth = roiWidth/5d;
		double[] regionLimits = {Double.NEGATIVE_INFINITY,minMax[0]+1d*regionWidth,minMax[0]+2d*regionWidth,minMax[0]+3d*regionWidth,minMax[0]+4d*regionWidth,Double.POSITIVE_INFINITY};
		double[] regionIndices = new double[rotatedRoi.length];
		for (int i = 0;i<rotatedRoi.length;++i){
			int sReg = 0;
			while (sReg<regionLimits.length && !(rotatedRoi[i][0]>regionLimits[sReg] && rotatedRoi[i][0]<=regionLimits[sReg+1])){
				++sReg;
			}
			regionIndices[i] = sReg;
			//IJ.log(rotatedRoi[i][0] +" "+regionIndices[i]+" lims "+regionLimits[1]+" "+regionLimits[2]+" "+regionLimits[3]+" "+regionLimits[4]+" ");
		}
		
		/**Color the subregions to visualize the division*/
		/*
		short[] tempPointer = Arrays.copyOf((short[]) imp.getProcessor().getPixels(),((short[])imp.getProcessor().getPixels()).length);
		ImagePlus tempImage = new ImagePlus("Subregion results");
		tempImage.setProcessor(new ShortProcessor(width,height,tempPointer,imp.getProcessor().getCurrentColorModel()));
		new ImageConverter(tempImage).convertToRGB();
		*/
		int[] rgb = new int[3];
		int value;
		for (int i = 0; i < fitArray.length;++i) {
			value = visualIP.getProcessor().getPixel((int)fitArray[i][0],(int)fitArray[i][1]);
			for (int c = 0; c<3;++c){
				rgb[c] = (value >>(c*8))& 0XFF;
			}
			switch ((int)regionIndices[i]){
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
			visualIP.getProcessor().drawPixel((int)fitArray[i][0],(int)fitArray[i][1]);
		}
		visualIP.show();
		visualIP.repaintWindow();

		/*Calculate results, and spit them out to log*/
		Calibration calib = imp.getCalibration();
		double heightScale = calib.pixelHeight;
		double widthScale = calib.pixelWidth;
		
		/*Calculate row by row width results*/
		double[] yMinMax = new double[]{tempRotated[1][minInd(tempRotated[1])],tempRotated[1][maxInd(tempRotated[1])]};
		ArrayList<Double> widths = new ArrayList<Double>();
		double tempWidth = 0;
		double pixNum = 0;
		for (int i = (int) Math.round(yMinMax[0]);i<=(int) Math.round(yMinMax[1]);++i){
			ArrayList<Double> rowData = new ArrayList<Double>();
			for (int j = 0;j<tempRotated[0].length;++j){
				if (((int) Math.round(tempRotated[1][j])) == i){
					rowData.add(tempRotated[0][j]);
				}
			}
			/**Check that any pixels were actually found*/
			if (!rowData.isEmpty()){
				Double[] xCoordinates = rowData.toArray(new Double[0]);
				widths.add(xCoordinates[maxInd(xCoordinates)]-xCoordinates[minInd(xCoordinates)]+1);
				//IJ.log("Row width "+widths.get(widths.size()-1)+" "+rowData.size());
				tempWidth+=widths.get(widths.size()-1);
				pixNum+=widths.get(widths.size()-1);
			}
			
		}
		
		/*Calculate weighted average for ROI width*/
		double weightedWidth = 0;
		for (int i = 0; i<widths.size();++i){
			weightedWidth+=widths.get(i)*widths.get(i)/pixNum;
		}
		
		/*Calculate sub region intensities*/
		ArrayList<Double>[] pixelInts = (ArrayList<Double>[]) new ArrayList[6];	/**First for mean, and then the subregions*/
		for (int i = 0;i<pixelInts.length;++i){
			pixelInts[i] = new ArrayList<Double>();
		}
		
		short[] tempPointer = Arrays.copyOf((short[]) imp.getProcessor().getPixels(),((short[])imp.getProcessor().getPixels()).length);
		for (int i = 0; i < fitArray.length;++i) {
			double pixelIntensity = tempPointer[(int)fitArray[i][0]+(int)fitArray[i][1]*width];
			pixelInts[0].add(pixelIntensity);
			pixelInts[(int)regionIndices[i]+1].add(pixelIntensity);
		}
		
		double[] intRes = new double[6];
		for (int i = 0;i<pixelInts.length;++i){
			for (int p = 0; p < pixelInts[i].size();++p) {
				intRes[i] += pixelInts[i].get(p);
			}
			intRes[i]/=(double)pixelInts[i].size();
		}
				
		/**Create (if it doesn't yet exist) a results panel*/
		TextPanel textPanel = IJ.getTextPanel();
		if (textPanel == null) {textPanel = new TextPanel("ROIAnalyzer results");}
		/*Add header if missing*/
		if (textPanel.getLineCount() == 0){
			String headerString = "area\t";
			headerString+="Width ["+calib.getUnit()+"]\tMean width ["+calib.getUnit()+"]\tWeighted mean width ["+calib.getUnit()+"]\tMean intensity\t";
			for (int i = 1;i<intRes.length;++i){
				headerString+="Region "+i+" intensity";
				if (i<intRes.length-1){
					headerString+="\t";
				}else{
					//headerString+="\n";
				}
			}
			textPanel.setColumnHeadings(headerString);
		}
		

		/*Print the results*/
		String resString = (roiPixels*widthScale*heightScale)+"\t";
		resString+=(roiWidth*widthScale)+"\t"+(tempWidth/widths.size()*widthScale)+"\t"+(weightedWidth*widthScale)+"\t"+intRes[0]+"\t";
		for (int i = 1;i<intRes.length;++i){
			resString+=intRes[i]+"\t";
		}
		textPanel.appendLine(resString);
		textPanel.updateDisplay();
		
		

				
		/*Re-activate the original stack*/
		WindowManager.setCurrentWindow(imw); 
		
		/*Visualize the polynomial fit*/
		/*
		Overlay olay = new Overlay();
		Line lineRoi = new Line(0d,coeffs[0]+coeffs[1]*0d,(double)width,coeffs[0]+coeffs[1]*((double)width));
		olay.add(lineRoi);
		imp.setOverlay(olay);
		*/
	
	}
	

	
	/**
		Rotate a 2D-vector with an angle. Positive angle leads into counter clockwise rotation
		@param vector a 2D vector to be rotated
		@param angle angle in radians to rotate
		@return rotated the rotated 2D vector
	*/
	private double[] rotateVector(double[] vector,double angle){
		double[] rotated = new double[2];
		rotated[0] = Math.cos(-angle)*vector[0]-Math.sin(-angle)*vector[1];
		rotated[1] = Math.sin(-angle)*vector[0]+Math.cos(-angle)*vector[1];
		return rotated;	
	}
	
	/**
		Get the index of minimum value within an array
		@param array the array to search for the minimum value for
		@return ind the index of the minimum value within the array
	*/
	
	private int minInd(Double[] array){
		double[] temp = new double[array.length];
		for (int i = 0;i<array.length;++i){
			temp[i] = array[i].doubleValue();
		}
		return minInd(temp);
	}
	
	private int maxInd(Double[] array){
		double[] temp = new double[array.length];
		for (int i = 0;i<array.length;++i){
			temp[i] = array[i].doubleValue();
		}
		return maxInd(temp);
	}
	
	private int minInd(double[] array){
		int ind = -1;
		double val = Double.MAX_VALUE;
		for (int i = 0;i<array.length;++i){
			if (array[i] < val){
				val = array[i];
				ind = i;
			}
		}
		return ind;
	}
	
	/**
		Get the index of maximum value within an array
		@param array the array to search for the maximum value for
		@return minI the index of the maximum value within the array
	*/
	private int maxInd(double[] array){
		int ind = -1;
		double val = Double.MIN_VALUE;
		for (int i = 0;i<array.length;++i){
			if (array[i] > val){
				val = array[i];
				ind = i;
			}
		}
		return ind;
	}
}
