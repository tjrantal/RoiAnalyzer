/**
	A class to store the subregion divisions, and to calculate the subregion results
*/

package edu.deakin.timo.utils;
import java.util.Arrays;
import java.util.ArrayList;
import ij.ImagePlus;
import ij.measure.Calibration;
import ij.text.TextPanel;
import ij.IJ;
import ij.io.FileInfo;

public class SubRegions{
	private ImagePlus imp;
	private PixelCoordinates pc;
	private int[] divisions;
	private double[] xMinMax;
	private double[] yMinMax;
	private int[][] subregions;
	private Calibration calib;
	private double heightScale;
	private double widthScale;
	private int width;
	private int height;
	/**Results to print*/
	public double[] widthWs;
	public double[] heightWs;
	public double meanIntensity;
	public double[][] subRegionIntensities;	 
	public double[][] subRegionAreas;
	public double[][] subRegionHeights;
	
	/**
		Constructor
		@param imp the ImagePlus for which the ROIs are defined
		@param pc ROI coordinates
		@param divisions 2D array for sub-regions {width divisions,height divisions}
	*/	
	public SubRegions(ImagePlus imp,PixelCoordinates pc, int[] divisions){
		this.imp		= imp;
		this.pc			= pc;
		this.divisions	= divisions;
		calib = imp.getCalibration();
		width = imp.getWidth();
		height = imp.getHeight();
		heightScale = calib.pixelHeight;
		widthScale = calib.pixelWidth;
		/**Calculate max width, and height from the rotated coordinates*/
		double[][] tempRotated = new double[2][pc.rotatedCoordinates.length];
		for (int i = 0;i<pc.rotatedCoordinates.length;++i){
			tempRotated[0][i] = pc.rotatedCoordinates[i][0];
			tempRotated[1][i] = pc.rotatedCoordinates[i][1];
		}
		xMinMax = new double[]{tempRotated[0][minInd(tempRotated[0])],tempRotated[0][maxInd(tempRotated[0])]};
		yMinMax = new double[]{tempRotated[1][minInd(tempRotated[1])],tempRotated[1][maxInd(tempRotated[1])]};
		/**Assign pixels to subregions*/
		subregions = new int[2][];
		subregions[0]	= assignPixelsToSubregions(tempRotated[0],xMinMax,divisions[0]);
		subregions[1]	= assignPixelsToSubregions(tempRotated[1],yMinMax,divisions[1]);
		/**Calculate sub-region results*/
		getSubregionResults(tempRotated);
	}
	
	public int[][] getSubregions(){
		return subregions;
	}
	
	/***/
	public void printResults(){
		/**Create (if it doesn't yet exist) a results panel*/
		TextPanel textPanel = IJ.getTextPanel();
		if (textPanel == null) {textPanel = new TextPanel("ROIAnalyzer results");}
		/*Add header if missing*/
		if (textPanel.getLineCount() == 0){
			String headerString = "StackName\tSliceNo\tarea\t";
			//Whole ROI results
			headerString+="Width ["+calib.getUnit()+"]\tMean width ["+calib.getUnit()+"]\tWeighted mean width ["+calib.getUnit()+"]\tHeight ["+calib.getUnit()+"]\tMean height ["+calib.getUnit()+"]\tWeighted mean height ["+calib.getUnit()+"]\tMean intensity\t";
			
			for (int j = 0;j<subRegionIntensities[0].length;++j){
				for (int i = 0;i<subRegionIntensities.length;++i){
					headerString+="Region r "+j+" c "+i+" intensity\t";
				}
			}
			for (int j = 0;j<subRegionAreas[0].length;++j){
				for (int i = 0;i<subRegionAreas.length;++i){
					headerString+="Region r "+j+" c "+i+" area\t";
				}
			}
			for (int j = 0;j<subRegionHeights[0].length;++j){
				for (int i = 0;i<subRegionHeights.length;++i){
					headerString+="Region r "+j+" c "+i+" weighted height ["+calib.getUnit()+"]\t";
				}
			}
			textPanel.setColumnHeadings(headerString);
		}

		/*Print the results*/
		String resString = "";
		resString += imp.getOriginalFileInfo().directory+imp.getTitle()+"_"+imp.getStack().getShortSliceLabel(imp.getSlice())+"\t";
		resString += imp.getSlice()+"\t";
		resString += (pc.roiPixels*widthScale*heightScale)+"\t";
		for (int i = 0; i<widthWs.length;++i){
			resString += (widthWs[i]*widthScale)+"\t";
		}
		for (int i = 0; i<heightWs.length;++i){
			resString += (heightWs[i]*heightScale)+"\t";
		}
		resString += (meanIntensity)+"\t";
		for (int j = 0;j<subRegionIntensities[0].length;++j){
			for (int i = 0;i<subRegionIntensities.length;++i){
				resString += (subRegionIntensities[i][j])+"\t";
			}
		}
		for (int j = 0;j<subRegionAreas[0].length;++j){
			for (int i = 0;i<subRegionAreas.length;++i){
				resString += (subRegionAreas[i][j]*widthScale*heightScale)+"\t";
			}
		}		
		for (int j = 0;j<subRegionHeights[0].length;++j){
			for (int i = 0;i<subRegionHeights.length;++i){
				resString += (subRegionHeights[i][j]*heightScale)+"\t";
			}
		}
		//public double[][] subRegionAreas;
		textPanel.appendLine(resString);
		textPanel.updateDisplay();
	}
	
	
	/**
		Calculate sub-region results
		@param tempRotated a 2 x N array with rotated X-, and Y-coordinates
	*/
	private void getSubregionResults(double[][] tempRotated){
		widthWs		= getRegionWidth(tempRotated,new int[]{0,1});
		heightWs	= getRegionWidth(tempRotated,new int[]{1,0});
		
		/*Calculate sub region intensities*/
		ArrayList<Double> meanInt = new ArrayList();
		ArrayList<Double>[][] regionInts = (ArrayList<Double>[][]) new ArrayList[divisions[0]][divisions[1]];
		ArrayList<Double[]>[][] regionCoords = (ArrayList<Double[]>[][]) new ArrayList[divisions[0]][divisions[1]];		
		for (int i = 0;i<regionInts.length;++i){
			for (int j = 0;j<regionInts[i].length;++j){
				regionInts[i][j] = new ArrayList<Double>();
				regionCoords[i][j] = new ArrayList<Double[]>();
			}
		}
		
		short[] tempPointer = Arrays.copyOf((short[]) imp.getProcessor().getPixels(),((short[])imp.getProcessor().getPixels()).length);
		for (int i = 0; i < pc.coordinates.length;++i) {
			double pixelIntensity = tempPointer[(int)pc.coordinates[i][0]+(int)pc.coordinates[i][1]*width];
			meanInt.add(pixelIntensity);
			regionInts[subregions[0][i]][subregions[1][i]].add(pixelIntensity);
			regionCoords[subregions[0][i]][subregions[1][i]].add(new Double[]{pc.coordinates[i][0],pc.coordinates[i][1]});
		}
		
		meanIntensity = 0;
		for (int p = 0; p < meanInt.size();++p) {
			meanIntensity += meanInt.get(p);
		}
		meanIntensity/=(double) meanInt.size();

		
		subRegionIntensities	= new double[divisions[0]][divisions[1]];	 
		subRegionAreas			= new double[divisions[0]][divisions[1]];
		subRegionHeights		= new double[divisions[0]][divisions[1]];		
		for (int i = 0; i<regionInts.length;++i){
			for (int j = 0;j<regionInts[i].length;++j){
				for (int p = 0; p < regionInts[i][j].size();++p) {
					subRegionIntensities[i][j] += regionInts[i][j].get(p);
				}
				subRegionIntensities[i][j]/=(double)regionInts[i][j].size();
				subRegionAreas[i][j] = (double)regionInts[i][j].size();
			}
		}
		//IJ.log("Subregions");
		/**Calculate subregion heights*/
		for (int i = 0; i<regionCoords.length;++i){
			for (int j = 0;j<regionCoords[i].length;++j){
				//Create temp rotated coordinates comprising just the subregion pixels
				double[][] tempRotatedRegion = new double[2][regionCoords[i][j].size()];
				for (int p = 0; p < regionCoords[i][j].size();++p) {
					tempRotatedRegion[0][p] = regionCoords[i][j].get(p)[0].doubleValue();
					tempRotatedRegion[1][p] = regionCoords[i][j].get(p)[1].doubleValue();
				}
				double temp[] = getRegionWidth(tempRotatedRegion,new int[]{1,0});
				subRegionHeights[i][j] = temp[2];
			}
		}
	}

	/**
		Calculate the width from x or y-coordinates
		@param tempRotated, a 2 x N array with rotated X-, and Y-coordinates
		@param ind whether to calculate the widths along the first ({0,1}), or the second {1,0} array.
		@return result 1D array with max width, mean width, and weighted mean width
	*/
	private double[] getRegionWidth(double[][] tempRotated,int[] ind){
		double[] result = new double[3];	//0 = width, 1 = average width, 2 = weighted width
		/*Calculate row by row width results*/
		ArrayList<Double> widths = new ArrayList<Double>();
		double tempWidth = 0;
		double pixNum = 0;
		xMinMax = new double[]{tempRotated[ind[0]][minInd(tempRotated[ind[0]])],tempRotated[ind[0]][maxInd(tempRotated[ind[0]])]};
		result[0] = (xMinMax[1]-xMinMax[0])+1d;	/*Max width in pixels*/
		yMinMax = new double[]{tempRotated[ind[1]][minInd(tempRotated[ind[1]])],tempRotated[ind[1]][maxInd(tempRotated[ind[1]])]};
		
		for (int i = (int) Math.round(yMinMax[0]);i<=(int) Math.round(yMinMax[1]);++i){
			ArrayList<Double> rowData = new ArrayList<Double>();
			for (int j = 0;j<tempRotated[ind[0]].length;++j){
				if (((int) Math.round(tempRotated[ind[1]][j])) == i){
					rowData.add(tempRotated[ind[0]][j]);
				}
			}
			/**Check that any pixels were actually found*/
			if (!rowData.isEmpty()){
				Double[] coordinates = rowData.toArray(new Double[0]);
				widths.add(coordinates[maxInd(coordinates)]-coordinates[minInd(coordinates)]+1);
				tempWidth+=widths.get(widths.size()-1);
				pixNum+=widths.get(widths.size()-1);
			}
			
		}
		result[1]=tempWidth/widths.size();	//mean width of rows
		/*Calculate weighted average for ROI width*/
		double weightedWidth = 0;
		for (int i = 0; i<widths.size();++i){
			weightedWidth+=widths.get(i)*widths.get(i)/pixNum;
		}
		result[2] = weightedWidth;
		return result;
	}
	
	/**
		Calculate ROI subdivisions.
		@param coordinates a 1D array of x or y coordinates
		@param minMax the minimum and maximum values of the 1D coordinate array
		@param divs the number of subdivisions to create
		@return regionIndices a 1D array of subregion assignments
	*/
	private int[] assignPixelsToSubregions(double[] coordinates, double[] minMax,int divs){
		double roiWidth = minMax[1]-minMax[0]+1d;
		double regionWidth = roiWidth/((double)divs);
		double[] regionLimits = new double[divs+1];
		for (int i = 1; i<divs;++i){
			regionLimits[i] = minMax[0]+(((double) i)*regionWidth);
		}
		regionLimits[0] = Double.NEGATIVE_INFINITY;
		regionLimits[regionLimits.length-1] = Double.POSITIVE_INFINITY;
		int[] regionIndices = new int[coordinates.length];
		int sReg;
		for (int i = 0;i<coordinates.length;++i){
			sReg = 0;
			while (sReg<regionLimits.length && !(coordinates[i]>regionLimits[sReg] && coordinates[i]<=regionLimits[sReg+1])){
				++sReg;
			}
			regionIndices[i] = sReg;
		}
		return regionIndices;
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
	
	/**
		Get the index of minimum value within an array
		@param array the array to search for the minimum value for
		@return ind the index of the minimum value within the array
	*/
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
		@return ind the index of the maximum value within the array
	*/
	private int maxInd(Double[] array){
		double[] temp = new double[array.length];
		for (int i = 0;i<array.length;++i){
			temp[i] = array[i].doubleValue();
		}
		return maxInd(temp);
	}
	
	/**
		Get the index of maximum value within an array
		@param array the array to search for the maximum value for
		@return ind the index of the maximum value within the array
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