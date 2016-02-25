/*
	This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.

	N.B.  the above text was copied from http://www.gnu.org/licenses/gpl.html
	unmodified. I have not attached a copy of the GNU license to the source...

    Copyright (C) 2011-2014 Timo Rantalainen
*/

package edu.deakin.timo.detectEdges;
import java.util.*;	//Vector, Collections
import java.lang.Math; //atan2
import java.awt.*;			//Polygon, Rectangle
import ij.*;		//ImagePlus
import ij.gui.*;	//ImagePlus ROI
import ij.text.*; 	//Debugging ...
import ij.process.*;	//Debugging

@SuppressWarnings(value ={"serial","unchecked"}) //Unchecked for cloning Vector<Integer>
public class EdgeDetector{


	private int height;
	private int width;
	private byte[][] image;
	byte threshold = 1;
	public Vector<DetectedEdge> edges;
	
	public EdgeDetector(byte[][] image){
		this.image = image;
		width =image.length;
		height =image[0].length;
		//IJ.log("w "+width+" h "+height);
		edges = findEdge();	//Trace bone edges	
	}

	/*DetectEdge*/
	Vector<DetectedEdge> findEdge(){
		int i,j,tempI,tempJ;
		int len;
		int sumArea = 0;
		i = 0;
		j = 0;
		byte[][] result = new byte[image.length][image[0].length];
		Vector<DetectedEdge> edges = new Vector<DetectedEdge>();

		while ((i < (width-1)) && (j < (height -1) )){
			while (j < height-1 && i < width && image[i][j] <1){
				++i;
				if (i < width && result[i][j] == 1){
					while (j < height-1 && result[i][j]>0){
						++i;
						if (i == width && j < height-2){
							i = 0;
							++j;
						}
						
					}
				}
				if (i == width){
					++j;
					if (j >= height-1) break;
					i = 0;
				}
			}
			tempI = i;
			tempJ = j;
			//IJ.log("found Init");
			if (i >= width-1 && j >= height-1){
				break;	/*Go to end...*/
			}
			result[i][j] = 1;

			/*Tracing algorithm DetectedEdge*/
			TracedEdge returned = traceEdge(result,i,j);
			//System.out.println("Traced Edge length "+returned.iit.size());

			
			/**Cleave traces*/
			Vector<TracedEdge> allEdges = new Vector<TracedEdge>();
			Vector<TracedEdge> cleavedEdges = new Vector<TracedEdge>();
			allEdges.add(returned);
			
			int checkEdge = 0;
			while (checkEdge < allEdges.size()){
				TracedEdge[] cleaved = cleaveEdge(allEdges.get(checkEdge),5d,6d);
				if (cleaved != null){
					for (int c = 0;c<cleaved.length;++c){
						allEdges.add(cleaved[c]);
						//System.out.println("Did cleave "+allEdges.size()+" edge length "+cleaved[c].iit.size());
					}
				}else{
					//System.out.println("Added edge "+cleavedEdges.size());
					cleavedEdges.add(allEdges.get(checkEdge));
				}
				++checkEdge;
			}

			
			/**Go through cleaved edges, and fill result edges*/
			//System.out.println("Fill edges "+cleavedEdges.size());
			for (int c = 0; c<cleavedEdges.size();++c){
				/**Trace the edge in the mask*/
				for (int e =0; e<cleavedEdges.get(c).iit.size();++e){
					result[cleavedEdges.get(c).iit.get(e)][cleavedEdges.get(c).jiit.get(e)] = 1;
				}
				/**Fill the found blob*/
				byte[][] fillReturn = fillResultEdge(result,cleavedEdges.get(c).iit,cleavedEdges.get(c).jiit);
				if (fillReturn != null){
					for (int x = 0;x<fillReturn.length;++x){
						for (int y = 0;y<fillReturn[x].length;++y){
							result[x][y] = fillReturn[x][y];
						}
					}
					int area = getMaskArea(result);
					edges.add(new DetectedEdge(cleavedEdges.get(c).iit,cleavedEdges.get(c).jiit,area-sumArea));
					sumArea+=area;
				}else{
					//System.out.println("Fill failed edge "+c);
				}
				
			}
			
			
			

			//Find next empty spot
			i = tempI;
			j = tempJ;
			while (j < height && image[i][j] >=threshold){
				i++;
				if (i == width){
				i = 0;
				j++;
				}
			}
		}
		return edges;
	}
	
	int getMaskArea(byte[][] a){
		int area =0;
		for (int i = 0;i<a.length;++i){
			for (int j = 0;j<a[i].length;++j){
				if (a[i][j]>=threshold){
					++area;
				}
			}
		}
		return area;
	}

	/*DetectedEdge version*/
	byte[][] fillResultEdge(byte[][] result,Vector<Integer> iit, Vector<Integer> jiit){
		if (iit.size() > 0){
			int kai,kaj;
			/*Set initial fill pixel to the first pixel above threshold not on the border*/
			/*Select the first pixel found*/
			boolean possible = true;

			int[] tempCoordinates = findFillInit(result, iit, jiit);
			if (tempCoordinates == null){
				possible = false;
			}
			while (possible){
				if (tempCoordinates == null){
					break;
				}else{
					FillResult returned = resultFill(tempCoordinates[0],tempCoordinates[1],result);
					possible = returned.success;
					result = returned.mask;
				}
				tempCoordinates = findFillInit(result, iit, jiit);
			}

			if (possible){
				return result;

			}
		}
		return null;
	}
	
		/*Cleaving is made by looking at the ratios of
	distances between two points along the edge and the shortest distance 
	between the points. If the maximum of the  ratio is big enough, the 
	highest ratio points will be connected with a straigth
	line and the edge with higher indices will be removed. E.g. 
	for a circle, the maximum ratio is (pi/2)/d ~= 1.57 and for square
	it is 2/sqrt(2) = sqrt(2) ~= 1.41.*/	
	TracedEdge[] cleaveEdge(TracedEdge edgeIn,double minRatio,double minLength){
		byte[][] result = new byte[edgeIn.result.length][edgeIn.result[0].length];
		for (int i =0;i<edgeIn.result.length;++i){
			for (int j =0;j<edgeIn.result[i].length;++j){
				result[i][j] = edgeIn.result[i][j];
			}
		}
		Vector<Integer> edgeI	=(Vector<Integer>) edgeIn.iit.clone();
		Vector<Integer> edgeJ	=(Vector<Integer>) edgeIn.jiit.clone();
		double distanceAlongTheEdge = 0;
		double distance = 0;
		double ratio;
		double minEdge = (double) edgeI.size()/minLength;
		int[] cleavingIndices = new int[2];
		boolean nextLoop = true;
		double highestRatio = minRatio-0.1;
		/*Go through all point pairs*/
		for (int i=0;i<edgeI.size()-((int) minLength+1);++i){
			for (int j=i+(int) minLength;j<edgeI.size();++j){
				distance = Math.sqrt(Math.pow((double) (edgeI.get(j)-edgeI.get(i)),2.0)+Math.pow((double) (edgeJ.get(j)-edgeJ.get(i)),2.0));
				distanceAlongTheEdge = min((double)(j-i),(double) edgeI.size()-j+i);
				if (distance == 0d){
					ratio = Double.POSITIVE_INFINITY;
				}else{
					ratio = distanceAlongTheEdge/distance;
				}
				if (ratio>highestRatio && distanceAlongTheEdge > minEdge){
					highestRatio = ratio;
					cleavingIndices[0] = i;
					cleavingIndices[1] = j;
				}

			}
		}
		/*If ratio is high enough, cleave at the highest ratio point pair*/
		if (highestRatio >= minRatio){
			return cleave(edgeIn,cleavingIndices);
		}else{
			return null;
		}
	}
	
	private double min(double a,double b){return a<b ? a:b;};
	
	/*	Remove the extra part from vectors and replace with a straight line	*/
	TracedEdge[] cleave(TracedEdge edgeIn,int[] cleavingIndices){
		byte[][] result = new byte[edgeIn.result.length][edgeIn.result[0].length];
		for (int i =0;i<edgeIn.result.length;++i){
			for (int j =0;j<edgeIn.result[i].length;++j){
				result[i][j] = edgeIn.result[i][j];
			}
		}
		Vector<Integer> edgeI	=(Vector<Integer>) edgeIn.iit.clone();
		Vector<Integer> edgeJ	=(Vector<Integer>) edgeIn.jiit.clone();
		int initialLength = edgeI.size();
		int initI = edgeI.get(cleavingIndices[0]);
		int initJ = edgeJ.get(cleavingIndices[0]);
		int targetI = edgeI.get(cleavingIndices[1]);
		int targetJ = edgeJ.get(cleavingIndices[1]);
		/*remove cleaved elements*/
		int replacementI = edgeI.get(cleavingIndices[0]);
		int replacementJ = edgeJ.get(cleavingIndices[0]);
		Vector<Integer> cleavedI = new Vector<Integer>(edgeI.subList(cleavingIndices[0]+1,cleavingIndices[1]+1)); /*the elements to be cleaved*/
		Vector<Integer> cleavedJ = new Vector<Integer>(edgeJ.subList(cleavingIndices[0]+1,cleavingIndices[1]+1)); /*the elements to be cleaved*/
		for (int i = cleavingIndices[0]; i <cleavingIndices[1];++i){
			edgeI.removeElementAt(cleavingIndices[0]);	/*Remove the elements to be cleaved*/
			edgeJ.removeElementAt(cleavingIndices[0]);	/*Remove the elements to be cleaved*/
		}
		/*Insert replacement line*/
		double replacementLength = (double)(cleavingIndices[1]-cleavingIndices[0]);
		double repILength = (double)(targetI-initI);
		double repJLength = (double)(targetJ-initJ);
		double relativeLength;
		Vector<Integer> insertionI = new Vector<Integer>();
		Vector<Integer> insertionJ = new Vector<Integer>();
		insertionI.add(replacementI);
		insertionJ.add(replacementJ);
		for (int k = cleavingIndices[0];k<cleavingIndices[1];++k){
			relativeLength = ((double)k)-((double)cleavingIndices[0]);
			replacementI = ((int) (repILength*(relativeLength/replacementLength)))+initI;
			replacementJ = ((int) (repJLength*(relativeLength/replacementLength)))+initJ;
			if (replacementI !=insertionI.lastElement() || replacementJ !=insertionJ.lastElement()){
				insertionI.add(replacementI);
				insertionJ.add(replacementJ);
				result[replacementI][replacementJ] = 1;
			}
		}
		edgeI.addAll(cleavingIndices[0],insertionI);
		edgeJ.addAll(cleavingIndices[0],insertionJ);
		Collections.reverse(insertionI);
		Collections.reverse(insertionJ);
		cleavedI.addAll(0,insertionI);
		cleavedJ.addAll(0,insertionJ);
		TracedEdge[] returnTraced = new TracedEdge[2];
		returnTraced[0] = new TracedEdge(result,edgeI,edgeJ);
		returnTraced[1] = new TracedEdge(result,cleavedI,cleavedJ);
		
		return returnTraced;
	}
	
	
	
	public class FillResult{
		public byte[][] mask;
		public boolean success;
		public FillResult(byte[][] mask, boolean success){
			this.mask = mask;
			this.success = success;
		}
	}
	
	
	/**Result fill*/
	FillResult resultFill(int i, int j, byte[][] tempResult){	
		Vector<Integer> initialI = new Vector<Integer>();
		Vector<Integer> initialJ= new Vector<Integer>();
		initialI.add(i);
		initialJ.add(j);
		int pixelsFilled = 0;
		while (initialI.size() >0 && initialI.lastElement() > 0 &&  initialI.lastElement() < width-1 && initialJ.lastElement() > 0 && initialJ.lastElement() < height-1){
			i =initialI.lastElement();
			j = initialJ.lastElement();
			initialI.remove( initialI.size()-1);
			initialJ.remove( initialJ.size()-1);

			if (tempResult[i][j] == 0 ){
				tempResult[i][j] = 1;
				++pixelsFilled;
			}

			if (tempResult[i-1][j] == 0) {
			initialI.add(i-1);
			initialJ.add(j);
			}

			if (tempResult[i+1][j] == 0) {
			initialI.add(i+1);
			initialJ.add(j);
			}
			
			if (tempResult[i][j-1] == 0) {
			initialI.add(i);
			initialJ.add(j-1);
			}
			
			if (tempResult[i][j] == 0) {
			initialI.add(i);
			initialJ.add(j+1);
			}

		}
		
		if (initialI.size() > 0 || initialJ.size()>0) {
			return new FillResult(tempResult,false);
		}else{
			return new FillResult(tempResult,true);
		}
	}
	
	/*DetectedEdge. Find fill init by steering clockwise from next to previous*/
	int [] findFillInit(byte[][] result, Vector<Integer> iit, Vector<Integer> jiit){
		int[] returnCoordinates = new int[2];
		int[][] pixelNeigbourhood = {{0,-1,-1,-1,-1,0,1,1,1},{1,1,0,-1,-1,-1,0,1}};
		int[] steer = new int[2];
		for (int j = 0; j< iit.size()-1; ++j){
			returnCoordinates[0] = iit.get(j);
			returnCoordinates[1] = jiit.get(j);
			double direction = Math.atan2(jiit.get(j+1)-returnCoordinates[1],iit.get(j+1)-returnCoordinates[0]);
			for (int i = 0; i< 8; ++i){
				direction+=Math.PI/4.0;
				steer[0] = (int) Math.round(Math.cos(direction));
				steer[1]= (int) Math.round(Math.sin(direction));
				/*Handle OOB*/
				while ((returnCoordinates[0]+steer[0])<0 || (returnCoordinates[0]+steer[0])>=width ||
						(returnCoordinates[1]+steer[1])<0 || (returnCoordinates[1]+steer[1])>=height){
					direction+=Math.PI/4.0;
					steer[0] = (int) Math.round(Math.cos(direction));
					steer[1]= (int) Math.round(Math.sin(direction));
				}
				
				if (result[returnCoordinates[0]+steer[0]][returnCoordinates[1]+steer[1]] == 0 
					&& image[returnCoordinates[0]+steer[0]][returnCoordinates[1]+steer[1]] >= threshold){
					returnCoordinates[0] +=steer[0];
					returnCoordinates[1] +=steer[1];
					return returnCoordinates;
				}
				if (result[returnCoordinates[0]+steer[0]][returnCoordinates[1]+steer[1]] == 1){
					break;
				}				
			}
		}
		return null;
	}
	
	
	/*	Edge Tracing DetectedEdge 
		trace edge by advancing according to the previous direction
		if above threshold, turn to negative direction
		if below threshold, turn to positive direction
		Idea taken from http://www.math.ucla.edu/~bertozzi/RTG/zhong07/report_zhong.pdf
		The paper traced continent edges on map/satellite image
	*/
	TracedEdge traceEdge(byte[][] result,int i,int j){
		Vector<Integer> iit 	= new Vector<Integer>();
		Vector<Integer> jiit	= new Vector<Integer>();
		iit.add(i);
		jiit.add(j);
		
		double direction = 0; //begin by advancing right. Positive angles rotate the direction clockwise.
		double previousDirection;
		boolean done = false;
		int initI,initJ;
		initI = i;
		initJ = j;
		
		while(true){
			int counter = 0;
			previousDirection = direction;
			/*Handle going out of bounds by considering out of bounds to be  less than threshold*/
			if (i+((int) Math.round(Math.cos(direction)))  >=0 && i+((int) Math.round(Math.cos(direction)))  < width
				&& j+((int) Math.round(Math.sin(direction)))  >=0 && j+((int) Math.round(Math.sin(direction)))  < height
				&& image[i+((int) Math.round(Math.cos(direction)))][j+((int) Math.round(Math.sin(direction)))] >= threshold
				 ){//Rotate counter clockwise
				while((image[i+((int) Math.round(Math.cos(direction-Math.PI/4.0)))][j+((int) Math.round(Math.sin(direction-Math.PI/4.0)))] >= threshold 
				)
				&& counter < 8
				&& i+((int) Math.round(Math.cos(direction-Math.PI/4.0)))  >=0 && i+((int) Math.round(Math.cos(direction-Math.PI/4.0)))  < width
				&& j+((int) Math.round(Math.sin(direction-Math.PI/4.0)))  >=0 && j+((int) Math.round(Math.sin(direction-Math.PI/4.0)))  < height
				){
					direction-=Math.PI/4.0;
					++counter;
					if (Math.abs(direction-previousDirection) >= 180){
						break;
					}
					
				}
			}else{//Rotate clockwise
				while((
				i+((int) Math.round(Math.cos(direction)))  <0 || i+((int) Math.round(Math.cos(direction)))  >= width || 
				j+((int) Math.round(Math.sin(direction)))  <0 || j+((int) Math.round(Math.sin(direction)))  >= height || 				
				image[i+((int) Math.round(Math.cos(direction)))][j+((int) Math.round(Math.sin(direction)))] < threshold				
				) && counter < 8){
					direction+=Math.PI/4.0;
					++counter;
					if (Math.abs(direction-previousDirection) >= 180){
						break;
					}
				}

			}
			i += (int) Math.round(Math.cos(direction));
			j += (int) Math.round(Math.sin(direction));
			if ((i == initI && j == initJ) || counter > 7 || image[i][j]<threshold || result[i][j] ==1 || result[i][j] >3){
				for (int ii = 0; ii< result.length;++ii){
					for (int jj = 0; jj< result[ii].length;++jj){
						if(result[ii][jj] > 1){
							result[ii][jj]=1;
						}
					}
				}
				return new TracedEdge(result,iit,jiit);
			}else{
				if (result[i][j] == 0){
					result[i][j] = 2;
				}else if (result[i][j] != 1){
					result[i][j]++;
				}
				iit.add(i);
				jiit.add(j);

			}
			direction -=Math.PI/2.0; //Keep steering counter clockwise not to miss single pixel structs...
		}		
	}

}
