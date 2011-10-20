package net.minecraft.src;
/*
//  By formivore 2011 for Minecraft Beta.
//	Builds towers
 */

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Random;

public class TemplateTML
{
	public TemplateRule[] rules;
	public int [][][] template=null;
	public boolean [][] templateLayout=null;
	public HashMap<String,int[][]> namedLayers=null;
	public HashMap<String,String> extraOptions=null;

	public String name="";
	//public int[] targets;
	public int height = 0, length = 0, width = 0, weight = 1, embed = 1,leveling = 4, cutIn = 0;
	//public int overhang = 0, primary=4, w_off=0, l_off=0, lbuffer =0;
	//public boolean preserveWater = false, preserveLava = false, preservePlants = false, unique = false;

	PrintWriter lw;

	public TemplateTML( File file, PrintWriter lw_) throws Exception{
		// load in the given file as a template
		BufferedReader br=null;
		try {
			name=file.getName();
			lw=lw_;
			ArrayList<String> lines = new ArrayList<String>();
			br= new BufferedReader( new FileReader( file ) );
			for(String read=br.readLine(); read!=null; read=br.readLine())
				lines.add(read);
			br.close();
			parseFile( lines );
			lw.println( "Successfully loaded template " + name + " with weight "+weight+".");
		} catch ( Exception e ) {
			throw e;
		}finally{ try{ if(br!=null) br.close();} catch(IOException e){} }
	}
	
	//****************************  FUNCTION - setFixedRules *************************************************************************************//
	public void setFixedRules(Random random){
		for(TemplateRule rule : rules)
			rule.setFixedRule(random);
	}

	//****************************  FUNCTION - parseFile *************************************************************************************//
	private void parseFile( ArrayList<String> lines ) throws Exception {
		namedLayers=new HashMap<String,int[][]>();
		ArrayList<int[][]> layers= new ArrayList<int[][]>();
		ArrayList<TemplateRule> rulesArrayList= new ArrayList<TemplateRule>();
		extraOptions=new HashMap<String,String>();

		// the first rule added will always be the air block rule.
		rulesArrayList.add( TemplateRule.AIR_RULE);

		// now get the rest of the data
		Iterator<String> itr = lines.iterator();
		int layerN=0;
		String line;
		while( itr.hasNext() ) {
			line=(itr.next().split("#")[0]).trim();
			if( line.startsWith( "layer" ) ) {

				//if layer has a label, put it in separate table. Otherwise add to main template.
				String[] layerData=line.split(":");
				if(layerData.length==1){
					layers.add(new int[length][width]);
					parseLayer(itr,layers.get(layers.size()-1));

					layerN++;
				} else if(layerData.length==2){
					namedLayers.put(layerData[1],new int[length][width]);
					parseLayer(itr,namedLayers.get(layerData[1]));
				}

			} else if( line.startsWith( "rule" ) ) {
				String[] parts = line.split( "=" );
				rulesArrayList.add( new TemplateRule(parts[1] ) );
			}
			else if(line.startsWith( "dimensions" )){
				int[] dim=WallStyle.readIntList(lw,null,"=",line);
				if(dim==null || dim.length!=3)
					throw new Exception( "Bad dimension input!" );
				height = dim[0];
				length = dim[1];
				width = dim[2];
			}
			//else if(line.startsWith("acceptable_target_blocks" )) targets=WallStyle.readIntList(lw,targets,"=",line);
			else if(line.startsWith("weight" )) weight = WallStyle.readIntParam(lw,weight,"=",line);
			else if(line.startsWith("embed_into_distance" )) embed = WallStyle.readIntParam(lw,embed,"=",line);
			else if(line.startsWith("max_cut_in" )) cutIn = WallStyle.readIntParam(lw,cutIn ,"=",line);
			else if(line.startsWith("max_leveling" )) leveling = WallStyle.readIntParam(lw,leveling ,"=",line);
			else if(line!=null && line.length()>0){
				String[] spl=line.split("=");
				if(spl.length==2 && !spl[0].equals("") && !spl[1].equals("") )
					extraOptions.put(spl[0],line); //lazy - put line as value since we have a functions to parse
			}
		}
		
		if(layers.size()==0) throw new Exception("No layers provided!");
		if(layers.size()!=height){
			lw.println("Warning, number of layers provided did not equal height.");
			height=layers.size();
		}
		template=new int[height][length][width];
		template=layers.toArray(template);
		
		if(weight<0) weight=0;


		//convert rules to array and check that rules in template are OK
		rules=new TemplateRule[rulesArrayList.size()];
		rules=rulesArrayList.toArray(rules);
		for(int z=0;z<height;z++)
			for(int x=0;x<length;x++)
				for(int y=0;y<width;y++)
					if(template[z][x][y]>=rules.length || template[z][x][y]<0)
						throw new Exception( "No rule provided for rule at ("+z+","+x+","+y+"): " +template[z][x][y]+" in template!" );
	}

	//****************************  FUNCTION - parseLayer *************************************************************************************//
	private void parseLayer(Iterator<String> itr, int[][] layer) throws Exception{
		// add in data until we reach the end of the layer
		String line = itr .next();
		int widthN=length-1;
		while( ! line.startsWith( "endlayer" ) ) {
			if( line.charAt( 0 ) != '#' ) {

				String[] rowdata=line.split(",");
				for( int y = 0; y < width; y++ ) {
					if(y<rowdata.length)
						layer[widthN][y]=Integer.parseInt(rowdata[y]);
					//else layer[widthN][y]=Building.PRESERVE_ID;
				}
			}
			line = itr .next();
			widthN--;
		}

		//fill in any left over lines
		//for(;widthN>=0;widthN--)
		//	for( int y = 0; y < length; y++ )
		//		layer[widthN][y]=Building.PRESERVE_ID;
	}


	//****************************  FUNCTION - parseVariables *************************************************************************************//
	/*
	private void parseVariables( ArrayList<String> variables ) throws Exception {
		Iterator<String> i = variables.iterator();
		String line;
		extraOptions=new HashMap<String,String>();

		while( i.hasNext() ) {
			line = i.next();

			if( ! line.startsWith( "#" ) ) {

				if(line.startsWith( "dimensions" )){
					int[] dim=WallStyle.readIntList(lw,null,"=",line);
					if(dim==null || dim.length!=3)
						throw new Exception( "Bad dimension input!" );
					height = dim[0];
					length = dim[1];
					width = dim[2];
				}
				else if(line.startsWith("acceptable_target_blocks" )) targets=WallStyle.readIntList(lw,targets,"=",line);
				else if(line.startsWith("weight" )) weight = WallStyle.readIntParam(lw,weight,"=",line);
				//else if(line.startsWith("unique" )) unique = WallStyle.readIntParam(lw,0,"=",line)==1;
				else if(line.startsWith("embed_into_distance" )) embed = WallStyle.readIntParam(lw,embed,"=",line);
				//else if(line.startsWith("primary_block" )) primary = WallStyle.readIntParam(lw,primary,"=",line);
				//else if(line.startsWith("allowable_overhang" )) overhang = WallStyle.readIntParam(lw,overhang ,"=",line);
				else if(line.startsWith("max_cut_in" )) cutIn = WallStyle.readIntParam(lw,cutIn ,"=",line);
				//else if(line.startsWith("cut_in_buffer" )) cbuffer = WallStyle.readIntParam(lw,cbuffer ,"=",line);
				else if(line.startsWith("max_leveling" )) leveling = WallStyle.readIntParam(lw,leveling ,"=",line);
				//else if(line.startsWith("leveling_buffer" )) lbuffer = WallStyle.readIntParam(lw,lbuffer ,"=",line);
				//else if(line.startsWith("preserve_water" )) preserveWater = WallStyle.readIntParam(lw,0,"=",line)==1;
				//else if(line.startsWith("preserve_lava" )) preserveLava = WallStyle.readIntParam(lw,0,"=",line)==1;
				//else if(line.startsWith("preserve_plants" )) preservePlants= WallStyle.readIntParam(lw,0,"=",line)==1;
				else if(line!=null){
					String[] spl=line.split("=");
					if(spl.length==2 && !spl[0].equals("") && !spl[1].equals("") )
						extraOptions.put(spl[0],line); //lazy - put line as value since we have a functions to parse
				}

			}
		}

		if(targets==null || targets.length < 1 )  targets= new int[]{0};

		//if(cbuffer > 5) cbuffer = 5;
		//if(lbuffer > 5) lbuffer = 5;
		if(weight<0) weight=0;

		//w_off = width%2==1 ? 0 - (width-1)/2 : 0 - width/2;
		//l_offoverhang = 0 = length%2==1 ? 0 - (width-1)/2 : 0 - width/2;

	}
	*/
	
	//****************************  FUNCTION - buildLayout *************************************************************************************//
	public TemplateTML buildLayout(){
		templateLayout=new boolean[length][width];
		for(int y=0; y<length;y++)
    		for(int x=0; x<width;x++)
    			templateLayout[y][x]=!rules[template[embed][y][x]].isPreserveRule();
		return this;
	}

	//****************************  FUNCTION - buildWeightsAndIndex *************************************************************************************//
	public static int[][] buildWeightsAndIndex(ArrayList<TemplateTML> templates, int nullWeight){
		int[][] weightsAndIndex=new int[2][templates.size()];
		int sum=0;
		TemplateTML temp;
		Iterator<TemplateTML> itr = templates.iterator();
		for(int m=0;itr.hasNext();m++){ 
			temp=itr.next();
			weightsAndIndex[0][m]=temp==null ? nullWeight : temp.weight;
			sum+=temp==null ? nullWeight : temp.weight;
			weightsAndIndex[1][m]=m;
		}
		if(sum==0) return buildWeightsAndIndex(templates, 1);

		return weightsAndIndex;
	}

	//****************************  FUNCTION - printTemplate*************************************************************************************//
	public void printTemplate(){
		System.out.println("TEMPLATE - " + name);
		for(int z=0;z<height;z++){
			for(int x=0;x<length;x++){
				for(int y=0;y<width;y++){
					System.out.print(template[z][x][y]+",");
				}
				System.out.println();
			}
			System.out.println("endLayer\n");
		}

	}

}