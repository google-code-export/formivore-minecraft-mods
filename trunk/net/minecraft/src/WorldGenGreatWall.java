package net.minecraft.src;
/*
//  By formivore 2011 for Minecraft Beta.
//	Builds a bi-directional great wall
 */

import java.util.Random;
import java.lang.Math;

public class WorldGenGreatWall extends WorldGeneratorThread
{
	//private final static boolean DEBUG=false;

	//**** WORKING VARIABLES **** 
	private mod_GreatWall gw;
	
	//****************************  CONSTRUCTOR - WorldGenGreatWall *************************************************************************************//
	public WorldGenGreatWall (mod_GreatWall gw_, BuildingExplorationHandler beh_,World world_, Random random_, int chunkI_, int chunkK_, int TriesPerChunk_, double ChunkTryProb_) { 
		super(beh_, world_, random_, chunkI_, chunkK_, TriesPerChunk_, ChunkTryProb_);
		gw=gw_;
		setName("WorldGenGreatWallThread");
	}

	//****************************  FUNCTION - generate  *************************************************************************************//
	@Override
	public boolean generate(int i0, int j0, int k0) throws InterruptedException{
		WallStyle ws=WallStyle.pickBiomeWeightedWallStyle(gw.wallStyles,world,i0,k0,random,false);
		if(ws==null) return false;
		
		
		BuildingDoubleWall dw=new BuildingDoubleWall(10*(random.nextInt(9000)+1000),this,ws,Building.pickDir(random),1,new int[] {i0,j0,k0});
		if(!dw.plan()) return false;

		//calculate the integrated curvature
		if(gw.CurveBias>0.01){
			//Perform a probabalistic test
			//Test formula considers both length and curvature, bias is towards longer and curvier walls.
			double curviness=0;
			for(int m=1;m<dw.wall1.bLength;m++) 
				curviness+= (dw.wall1.xArray[m]==dw.wall1.xArray[m-1] ? 0:1)+(dw.wall1.zArray[m]==dw.wall1.zArray[m-1] ? 0:1);
			for(int m=1;m<dw.wall2.bLength;m++) 
				curviness+= (dw.wall2.xArray[m]==dw.wall2.xArray[m-1] ? 0:1)+(dw.wall2.zArray[m]==dw.wall2.zArray[m-1] ? 0:1);
			curviness/=(double)(2*(dw.wall1.bLength+dw.wall2.bLength - 1));
			
			double p=gw.ACCEPT_ALPHA*(4.0*gw.CurveBias+1.0)*Math.pow(curviness,4.0*gw.CurveBias)*(dw.wall1.bLength+dw.wall1.bLength)/gw.LengthBiasNorm;
			
			//if(BuildingWall.DEBUG>1)
				System.out.println("Curviness="+curviness+", Length="+(dw.wall1.bLength+dw.wall1.bLength - 1)+", P="+p); 
			
			if(random.nextFloat() > p)
				return false;
		}

			dw.build(LAYOUT_CODE_NOCODE);
		dw.buildTowers(true,true,ws.GatehouseTowers,false,false);
		return true;
	}

}










