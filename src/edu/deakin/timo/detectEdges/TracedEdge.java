package edu.deakin.timo.detectEdges;
import java.util.*;	//Vector, Collections
public class TracedEdge{
	public byte[][] result;
	Vector<Integer> iit;
	Vector<Integer> jiit;
	public TracedEdge(byte[][] result,Vector<Integer> iit,Vector<Integer> jiit){
		this.result = result;
		this.iit = iit;
		this.jiit = jiit;
	}
}