package edu.deakin.timo.utils;

/**
	Helper class to contain coordinates to be stored in an ArrayList
*/
public class Coordinate implements Comparable<Coordinate>{
	public double x;
	public double y;
	public Coordinate(double x, double y){
		this.x = x;
		this.y = y;
	}
	
	/*
		Implement Comparable based on y-coordinate
		For Ascending order
	*/
	@Override
    public int compareTo(Coordinate a) {
		return this.y <= a.y ? -1:1; //which is larger
    }
}