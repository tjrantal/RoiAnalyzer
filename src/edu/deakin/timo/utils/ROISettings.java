/*
 Image/J Plugins
 Copyright (C) 2012 Timo Rantalainen
 Author's email: tjrantal at gmail dot com
 The code is licenced under GPL 2.0 or newer
 */
package edu.deakin.timo.utils;

/*Choosing and saving a file*/
import javax.swing.SwingUtilities;
import javax.swing.JFileChooser;
import java.util.prefs.Preferences;		/*Saving the file save path -> no need to re-browse...*/
import java.io.File;
import java.io.IOException;
/*Saving settings*/
import java.util.prefs.Preferences;		/*Saving the file save path -> no need to rebrowse...*/
/*The window frame*/
import javax.swing.*;	/*JFrame*/
import java.awt.*;		/*Layout*/

/*
 Settings for Roi tools
 */

public class ROISettings extends JFrame {
	
	private Preferences preferences;		/**Saving the default file path*/
	public final String[] keys = {"Sub-region Rows","Sub-region Columns","Root Directory","Results Sub-directory","Roi results directory","Visualize Sub-regions [<1 off]","ROI to read","Stack path","Rotation [0 all pix, 1 top row, 4 no rotation]","Erode ROIs","Print Widths","PolyFit [0 = no, else = order]"};
	private final String[] defaults = {"1","5","c:/Output","IVD","ROIs","0","c:/Output","C:/timo/research/BelavyQuittner2015/stacks","1","0","0","0"};
	private String[] settings;
	private JTextField[] textFields;

	
	/*Constructor*/
	public ROISettings(){
		super("ROISettings");
		settings = new String[keys.length];
		preferences = Preferences.userRoot().node(this.getClass().getName());
		//System.out.println("Get prefs from storage");
		for (int i = 0; i<keys.length;++i){
			settings[i] = preferences.get(keys[i],defaults[i]); /*Use current working directory as default*/
			//System.out.println("Storage "+keys[i]+" "+settings[i]);
		}
		textFields = new JTextField[keys.length];
		/*Add Textfields, and tickboxes*/
		//frame = new JFrame("EncriclerOptions");	//Add frame
		JPanel selections = new JPanel();
		selections.setLayout(new GridLayout(keys.length,2,5,5));
		/*Settings*/
		for (int i = 0;i<keys.length;++i){
			selections.add(new JLabel(keys[i]));
			textFields[i] = new JTextField(settings[i],50);
			selections.add(textFields[i]);
		}

		/*Make frame visible*/
		selections.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		selections.setPreferredSize(new Dimension(300,40*keys.length));
		this.getContentPane().add(selections);
		this.setLocation(20,20);
		this.pack();
		this.setVisible(true);
		this.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);	//Only hide the window instead of disposing
		//this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		//this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		//this.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
	}

	public void saveSettings(){
		/*Save Preferences*/
		//System.out.println("Saving prefs");
		getSettings();
		for (int i = 0; i<keys.length;++i){
			preferences.put(keys[i],settings[i]);
			//System.out.println(keys[i]+" "+settings[i]);
		}

	}
	
	/*Set fields*/
	public void setTextField(String fieldName,String fieldVal){
		int match = 0;
		while (match < keys.length && !keys[match].equals(fieldName)){
			++match;
		}
		textFields[match].setText(fieldVal);
	}
	
	public String[] getSettings(){
		try{
			for (int i = 0; i<textFields.length;++i){
				settings[i] = textFields[i].getText();
			}
		}catch (Exception err){
			System.out.println(err);
		}
		return settings;
	}
	
	
}
