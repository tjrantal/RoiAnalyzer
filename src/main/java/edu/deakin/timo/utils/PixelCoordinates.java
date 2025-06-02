/**
	A class to store the original, and rotated ROI pixel coordinates, and the subregion divisions. Subregion indices are assigned in row major order. Working out the rotation angle, and the rotation are also implemented in this class
*/

package edu.deakin.timo.utils;
import ij.IJ;
import ij.gui.NewImage;			/*For creating the output stack images*/
import ij.ImagePlus;
import java.util.*;	//Vector, Collections
import edu.deakin.timo.detectEdges.EdgeDetectorRoiAnalyser;
import edu.deakin.timo.detectEdges.DetectedEdge;

//JTS for rectangle fit from maven org.locationtech.jts jts-core
import org.locationtech.jts.algorithm.MinimumAreaRectangle;
import org.locationtech.jts.algorithm.ConvexHull;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.operation.distance.DistanceOp;

public class PixelCoordinates{
	public double[][] coordinates;
	public double[][] rotatedCoordinates;
	public double[] centreCoordinates;
	public int roiPixels;
	public double angle;

	/***Helper construtor*/
	public PixelCoordinates(byte[] mask,int width, int height,int rotationSelection){
		this(mask,width,height,rotationSelection,false);
	}

	/**
		Constructor
		@param mask the byte mask for calculating ROI pixel coordinates for
		@param width the width of the image the mask corresponds to
		@param height the height of the image the mask corresponds to
		@param useRectangle whether to use minimal rectangle fit to figure out ROI corners
	*/
	
	public PixelCoordinates(byte[] mask,int width, int height,int rotationSelection, boolean useRectangle){
		roiPixels = 0;
		for (int i = 0; i<mask.length;++i){
			if (mask[i]==1){
				++roiPixels;
			}
		}
		coordinates = new double[roiPixels][2];
		centreCoordinates = new double[2];
		roiPixels = 0;
		for (int j = 0;j< height;j++){
			for (int i = 0; i < width;i++){
				if (mask[i+j*width] == 1){
					coordinates[roiPixels][0] = i;	//x-coordinate
					coordinates[roiPixels][1] = j;	//y-coordinate
					centreCoordinates[0]+=(double) i;
					centreCoordinates[1]+=(double) j;
					++roiPixels;
				}
			}
		}
		centreCoordinates[0]/=(double)roiPixels;
		centreCoordinates[1]/=(double)roiPixels;
		
		angle = 0;
		double angleTop, angleBottom;
		double[][] pixelsForRotation=  null;
		double[][][] sideCoords;
		switch (rotationSelection){
			case 0:
				//Get rotationAngle based on all Roi Pixels
				pixelsForRotation = coordinates;
				angle = getRotationAngle(pixelsForRotation);
				////IJ.log("All");
				break;
			case 1:
				//Get rotation angle based on top row of pixels
				sideCoords = getRoiSideCoordinates(centreCoordinates,mask,width,height,useRectangle);
				pixelsForRotation = sideCoords[0];
				angle = getRotationAngle(pixelsForRotation);
				////IJ.log("Top");
				//IJ.log(String.format("Top %.1f deg x %.1f y %.1f  x %.1f y %.1f ",angle/Math.PI*180d,pixelsForRotation[0][0],pixelsForRotation[0][1],pixelsForRotation[pixelsForRotation.length-1][0],pixelsForRotation[pixelsForRotation.length-1][1]));
				break;
			case 2:
				//Get rotation angle based on bottom row of pixels
				sideCoords = getRoiSideCoordinates(centreCoordinates,mask,width,height,useRectangle);
				pixelsForRotation = sideCoords[2];
				angle = getRotationAngle(pixelsForRotation);
				////IJ.log("Bottom");
				break;
			case 3:
				//Get rotation angle based on top and bottom row of pixels
				sideCoords = getRoiSideCoordinates(centreCoordinates,mask,width,height,useRectangle);
				pixelsForRotation = sideCoords[0];
				double topAngle = getRotationAngle(pixelsForRotation);
				pixelsForRotation = sideCoords[2];
				double bottomAngle = getRotationAngle(pixelsForRotation);
				angle = (topAngle+bottomAngle)/2d;
				//IJ.log(String.format("Bottom and Top %.1f",angle/Math.PI*180d));
				break;
			case 4:
				//Set rotation to zero
				angle = 0d;
				//IJ.log("No rotation");
				break;
			case 5:
				//Get rotation as the average of top corner to top corner and bottom corner to bottom corner
				sideCoords = getRoiSideCoordinates(centreCoordinates,mask,width,height,useRectangle);
				angleTop  = Math.acos((Utils.getUnit(new double[]{sideCoords[1][0][0]-sideCoords[0][0][0],sideCoords[1][0][1]-sideCoords[0][0][1]}))[0]);
				angleBottom  = Math.acos((Utils.getUnit(new double[]{sideCoords[2][0][0]-sideCoords[3][0][0],sideCoords[2][0][1]-sideCoords[3][0][1]}))[0]);
				angle = -(angleTop+angleBottom)/2d;
				//IJ.log(String.format("Bottom and Top Corners %.1f",angle/Math.PI*180d));
				break;
			case 6:
				//Rotate the top line to horizontal clock-wise
				sideCoords = getRoiSideCoordinates(centreCoordinates,mask,width,height,useRectangle);
				angleTop  = Math.acos((Utils.getUnit(new double[]{sideCoords[1][0][0]-sideCoords[0][0][0],sideCoords[1][0][1]-sideCoords[0][0][1]}))[0]);
				angleBottom  = Math.acos((Utils.getUnit(new double[]{sideCoords[2][0][0]-sideCoords[3][0][0],sideCoords[2][0][1]-sideCoords[3][0][1]}))[0]);
				angle = -(angleTop+angleBottom)/2d;
				//IJ.log(String.format("Bottom and Top Corners %.1f",angle/Math.PI*180d));
				break;
		}
		
		////IJ.log("Angle "+angle);
			
		
		////IJ.log("Angle "+angle/Math.PI*180d);
		//Calculate rotated coordinates
		rotatedCoordinates = new double[coordinates.length][2];
		for (int i = 0;i<coordinates.length;++i){
			double[] temp = rotateVector(new double[]{coordinates[i][0],coordinates[i][1]},angle);
			rotatedCoordinates[i][0] = temp[0];
			rotatedCoordinates[i][1] = temp[1];
		}
	}

	/**Helper call*/
	private double[][][] getRoiSideCoordinates(double[] centreCoordinates,byte[] mask,int width, int height) {
		return getRoiSideCoordinates(centreCoordinates,mask,width, height,false);
	}

	/**
		Get rotation angle for a ROI to align the top row  with the horizon
		
		@param coordinates N x 2 array of ROI pixel coordinates
		@param centreCoordinates ROI centre coordinates
		@return required rotation angle
		EdgeDetector
	*/
	private double[][][] getRoiSideCoordinates(double[] centreCoordinates,byte[] mask,int width, int height, boolean useRectangle) {
		////IJ.log("Create EdgeDetector");
		//DetectedEdge test = new DetectedEdge(new Vector<Integer>(),new Vector<Integer>());
		EdgeDetectorRoiAnalyser edge = new EdgeDetectorRoiAnalyser(mask, width, height);
		////IJ.log("Vector Detected Edge");
		Vector<DetectedEdge> edges = edge.edges;

		if (useRectangle) {
			return getPolarCornersFromRectangle(edges.get(0), centreCoordinates);	//Use smalles rectangle to figure out corners
		}else{
			return getSidePixels(edges.get(0), centreCoordinates);    //The tangent of the rotation angle is coeffs[1]/1
		}
		//Visualise the mask here
		/*
		ImagePlus resultImage = NewImage.createByteImage("Mask visualisation",width,height,1, NewImage.FILL_BLACK);
		byte[] rPixels = (byte[])resultImage.getProcessor().getPixels();
		for (int r = 0;r<height;++r){
			for (int c = 0;c<width;++c){
				//rPixels[c+r*width] = lbpImage[c][r];
				rPixels[c+r*width] = mask[c+r*width];
			}
		}
		resultImage.setDisplayRange(0, 1);
        resultImage.show();
		
		//Print edge coordinates
		for (int i=0; i<edges.get(0).iit.size();++i){
			//IJ.log("edge x "+edges.get(0).iit.get(i)+" y "+edges.get(0).jiit.get(i));
		}
		*/

	}
	
	
	/**
		Get rotation angle for a ROI to align the long axis with horizon
		@param coordinates N x 2 array of ROI pixel coordinates
		@return required rotation angle
	*/
	private double getRotationAngle(double[][] coordinates){
		double[] coeffs = Utils.polynomialFit(coordinates, 1);
		return Math.atan(coeffs[1]);	/*The tangent of the rotation angle is coeffs[1]/1*/
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
	

	/*
	* Helper to compute distance from a coordinate with JTS
	* @coordinates an array of coordinates to check
	* @target the coordinate to check
	* @returns the index of the coordinate closest to the target
	* */
	public static int findNearest(Coordinate[] coordinates, Coordinate target) {
		if (coordinates == null || coordinates.length == 0 || target == null) {
			throw new IllegalArgumentException("Coordinates array and target must not be null or empty.");
		}

		double minDistance = target.distance(coordinates[0]);
		int minIndex = 0;

		for (int i = 0; i< coordinates.length; ++i) {
			double dist = target.distance(coordinates[i]);
			if (dist < minDistance) {
				minDistance = dist;
				minIndex = i;
			}
		}
		return minIndex;
	}

	/*
	*Determine the smallest rotated rectangle that the ROI fits through
	* Get points that are closest to the rectangle corners.
	* Order the points so that 0 to 1 is top with top defined as the side closest to horizontal next to the corner with the top-most y-coordinate
	* */
	public static double[][][] getPolarCornersFromRectangle(DetectedEdge edge, double[] centreCoords){
		//Test rectangle fit
		Coordinate[] coords = new Coordinate[edge.iit.size()];
		for (int i = 0;i<edge.iit.size();++i){
			coords[i] = new Coordinate(edge.iit.get(i),edge.jiit.get(i));
		}

		// Create a convex hull geometry
		ConvexHull hull = new ConvexHull(coords, new GeometryFactory());
		// Compute the minimum bounding rectangle
		Geometry minAreaRect = MinimumAreaRectangle.getMinimumRectangle(hull.getConvexHull());
		Coordinate[]	cornerCoordinates = minAreaRect.getCoordinates();
		//IJ.log("Minimum Bounding Rectangle: " + minAreaRect);
		for (int i = 0;i<cornerCoordinates.length;++i){
			//IJ.log("Coordinates: " + cornerCoordinates[i]);
		}

		//Get the closes coordinates
		int[] cornerIndices = new int[4];
		double[][] rectangleCornerCoordinates = new double[4][];
		for (int i = 0;i<4;++i) {
			int nearest = findNearest(coords, cornerCoordinates[i]);
			//IJ.log(String.format("Corner %d x %.1f y %.1f",i,coords[nearest].x,coords[nearest].y));
			cornerIndices[i] = nearest;
			rectangleCornerCoordinates[i] = new double[]{coords[nearest].x,coords[nearest].y};
		}


		double r,theta,a,b;
		Double[][] polar = new Double[rectangleCornerCoordinates.length][5];	/*0 r, 1 theta, 2 x, 3 y*/
		for (int i=0; i<rectangleCornerCoordinates.length;++i){
			polar[i][2] = (double) rectangleCornerCoordinates[i][0];
			polar[i][3] = (double) rectangleCornerCoordinates[i][1];
			polar[i][4] = (double) cornerIndices[i];
			a = polar[i][2]-centreCoords[0];
			b = polar[i][3]-centreCoords[1];
			r = Math.sqrt(Math.pow(a,2d)+Math.pow(b,2d));
			theta = Math.atan2(b,a);
			polar[i][0] = r;
			polar[i][1] = theta;
			//IJ.log(String.format("corner %d r %.1f theta %.1f",i,r,theta/Math.PI*180));
		}
		/*sort the coordinates, theta 0 = towards right, theta increases clock-wise in ImageJ image (where y increases towards bottom!*/
		/**Prepare a comparator*/
		Comparator<Double[]> comparator = new Comparator<Double[]>() {
			@Override
			public int compare(Double[] o1, Double[] o2) {
				return o1[1].compareTo(o2[1]);
			}
		};
		//IJ.log("Sort polar coords");
		Arrays.sort(polar,comparator);	/**Sort the polar coordinates into ascending order according to theta*/
		double[][] sortedCorners = new double[4][];

		int minY = Integer.MAX_VALUE;
		int minInd = -1;
		for (int i=0; i<rectangleCornerCoordinates.length;++i){
			sortedCorners[i] = new double[]{polar[i][0],polar[i][1],polar[i][4]};
			if (coords[(int) sortedCorners[i][2]].y < minY){
				minY = (int) coords[(int) sortedCorners[i][2]].y;
				minInd = i;
			}
			//IJ.log(String.format("polar corner %d r %.1f theta %.1f index %d",i,sortedCorners[i][0],sortedCorners[i][1],(int) sortedCorners[i][2]));
		}
		//Get previous and next corner indices
		int prevIndex = (minInd - 1 + sortedCorners.length) % sortedCorners.length;
		int nextIndex = (minInd + 1) % sortedCorners.length;

		//Check which side is closer to horizontal
		double dxNext = Math.abs(coords[(int) sortedCorners[nextIndex][2]].x - coords[(int) sortedCorners[minInd][2]].x);
		double dyNext = Math.abs(coords[(int) sortedCorners[nextIndex][2]].y - coords[(int) sortedCorners[minInd][2]].y);
		double dxPrev = Math.abs(coords[(int) sortedCorners[minInd][2]].x - coords[(int) sortedCorners[prevIndex][2]].x);
		double dyPrev = Math.abs(coords[(int) sortedCorners[minInd][2]].y - coords[(int) sortedCorners[prevIndex][2]].y);

		int index0, index1;
		if (dxNext / (dyNext + 1e-6) > dxPrev / (dyPrev + 1e-6)) {
			index0 = minInd;
			index1 = nextIndex;
		} else {
			index1 = minInd;
			index0 = prevIndex;
		}
		//IJ.log(String.format("Top Rectangle corners"));
		//IJ.log(String.format("index0 %d r %.1f theta %.1f index %d x %.1f y %.1f",index0,sortedCorners[index0][0],sortedCorners[index0][1],(int) sortedCorners[index0][2],coords[(int) sortedCorners[index0][2]].x,coords[(int) sortedCorners[index0][2]].y));
		//IJ.log(String.format("index1 %d r %.1f theta %.1f index %d x %.1f y %.1f",index1,sortedCorners[index1][0],sortedCorners[index1][1],(int) sortedCorners[index1][2],coords[(int) sortedCorners[index1][2]].x,coords[(int) sortedCorners[index1][2]].y));
        IJ.log("Got Into sort corners");
		//sort the corners so that top side is 0 to 1
		double[][] orderedCorners = new double[5][];
        int ii = 0;
		for (int i=index0; i<index0+sortedCorners.length;++i){
			int ind = i % sortedCorners.length;
            IJ.log(String.format("i %d ind %d sortedCorners.length %d",i,ind,sortedCorners.length));
			orderedCorners[ii] = new double[]{sortedCorners[ind][0],sortedCorners[ind][1],sortedCorners[ind][2]};
            ii +=1;
			//IJ.log(String.format("Corner %d r %.1f theta %.1f index %d x %.1f y %.1f",i,orderedCorners[i][0],orderedCorners[i][1],(int) sortedCorners[i][2],coords[(int) sortedCorners[i][2]].x,coords[(int) sortedCorners[i][2]].y));
		}
		orderedCorners[4] = orderedCorners[0];	//Close the loop

		//Create side coordinates here
		double[][][] sideCoordinates = new double[4][][];
		int cnt = 0;
		//The last side has to be handled manually
		for (int i = 0;i<orderedCorners.length-1;++i){
			int sourceInd = (int) orderedCorners[i][2];
			int targetInd = (int) orderedCorners[i+1][2];
			if (targetInd < sourceInd){
				targetInd+=coords.length;
			}
			sideCoordinates[i] = new double[(int) targetInd-sourceInd+1][2];
			cnt = 0;
			for (int j = sourceInd; j <=targetInd;++j){
				int ind = j % coords.length;
				sideCoordinates[i][cnt][0] = coords[ind].x;
				sideCoordinates[i][cnt][1] = coords[ind].y;
				++cnt;
			}
		}
		return sideCoordinates;
	}

	/**
		Determine the corners of the edge. Helper function for getRotationAngleTopRow
		@param edge The coordinates of the edge for corner detection
		@param centreCoords, the coordinates of the centre of the ROI the edge belongs to
		@return corners 4 by 2 array with x-, and y- coordinates of the corners
	
	*/
	public static double[][][] getSidePixels(DetectedEdge edge, double[] centreCoords){
		int[][] corners = null;
		/*Calculate the roi coordinates in polar coordinates*/
		double r,theta,a,b;
		Double[][] polar = new Double[edge.iit.size()][4];	/*0 r, 1 theta, 2 x, 3 y*/
		////IJ.log("Get polar coords "+edge.iit.size());
		for (int i=0; i<edge.iit.size();++i){
			polar[i][2] = (double) edge.iit.get(i);
			polar[i][3] = (double) edge.jiit.get(i);
			a = polar[i][2]-centreCoords[0];
			b = polar[i][3]-centreCoords[1];
			r = Math.sqrt(Math.pow(a,2d)+Math.pow(b,2d));
			theta = Math.atan2(b,a);
			polar[i][0] = r;
			polar[i][1] = theta;
			
		}
		/*sort the coordinates, theta 0 = towards right, theta increases clock-wise in ImageJ image (where y increases towards bottom!*/
		/**Prepare a comparator*/
		Comparator<Double[]> comparator = new Comparator<Double[]>() {
			@Override
			public int compare(Double[] o1, Double[] o2) {
				return o1[1].compareTo(o2[1]);
			}
		};
		//IJ.log("Sort polar coords");
		Arrays.sort(polar,comparator);	/**Sort the polar coordinates into ascending order according to theta*/
		/**Look for the four corners, take the maximum r in the four sectors -pi to -pi/2; -pi/2 to 0; 0 to pi/2; pi/2 to pi*/
		int ind = 0;
		double[][] polarCorners = new double[4][];
		////IJ.log("Find polar corners "+(polar[ind][1]/Math.PI*180d));
		for (int i = 0;i<4;++i){
			//IJ.log("Start corner "+i+" theta "+(polar[ind][1]/Math.PI*180d));
			double maxR = Double.NEGATIVE_INFINITY;
			double maxTheta = 0;
			double maxInd = -1;
			while (ind < polar.length && polar[ind][1] < ((2*Math.PI*((double)i+1d)/4d)-Math.PI)){
				//if (i ==3){
					////IJ.log("Current ind "+ind+" theta "+(polar[ind][1]/Math.PI*180d)+" r "+polar[ind][0]); 
				//}
				if (polar[ind][0] > maxR){
					maxR = polar[ind][0];
					maxTheta = polar[ind][1];
					maxInd = ind;
				}
				++ind;
			}
			//IJ.log("found corner "+i+" maxInd "+maxInd+" current ind "+ind+" of "+polar.length);
			polarCorners[i] = new double[]{maxR,maxTheta,maxInd};	
			//IJ.log("found corner "+i+" x "+ polar[(int)polarCorners[i][2]][2] + " y "+polar[(int)polarCorners[i][2]][3]);
		}

		//Test rotation
		//polarCorners = polarCornersRectangle;

		//KEEP GOING FROM HERE. HAVE TO ALLOW CHANGE OF INDEXING ON ANY ASPECT...
		//Get the side coordinates
		double[][][] sideCoordinates = new double[4][][];
		int cnt = 0;
		//The last side has to be handled manually
		//IJ.log(String.format("PolarCorners.length %d",polarCorners.length));
		for (int i = 0;i<polarCorners.length-1;++i){
			//IJ.log(String.format("(int) polarCorners[i+1][2],(int) polarCorners[i][2] %d %d",(int) polarCorners[i+1][2],(int) polarCorners[i][2]));

			sideCoordinates[i] = new double[(int) (Math.abs(polarCorners[i+1][2]-polarCorners[i][2])+1)][2];
			cnt = 0;
			if (polarCorners[i+1][2] > polarCorners[i][2]) {
				for (int j = (int) polarCorners[i][2]; j <= (int) polarCorners[i + 1][2]; ++j) {
					sideCoordinates[i][cnt][0] = polar[j][2];
					sideCoordinates[i][cnt][1] = polar[j][3];
					++cnt;
				}
			}else{
				for (int j = (int) polarCorners[i+1][2]; j >= (int) polarCorners[i][2]; --j) {
					sideCoordinates[i][cnt][0] = polar[j][2];
					sideCoordinates[i][cnt][1] = polar[j][3];
					++cnt;
				}
			}
		}
		//Final side
		sideCoordinates[3] = new double[(int) (polar.length-polarCorners[3][2]+polarCorners[0][2]+1)][2];
		cnt = 0;
		for (int j = (int) polarCorners[3][2]; j<polar.length;++j){
			sideCoordinates[3][cnt][0] = polar[j][2];
			sideCoordinates[3][cnt][1] = polar[j][3];
			++cnt;
		}
		for (int j = 0 ; j<=(int) polarCorners[0][2];++j){
			sideCoordinates[3][cnt][0] = polar[j][2];
			sideCoordinates[3][cnt][1] = polar[j][3];
			++cnt;
		}
		return sideCoordinates;
	}
}