package net.minecraft.src;
/*
 *  Source code for the The Great Wall Mod and Walled City Generator Mods for the game Minecraft
 *  Copyright (C) 2014 by Noah Whitman (wakatakeru@gmail.com)

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.
    
    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import java.util.Random;


public class BlockSurveyorsRod extends Block
{
	mod_GreatWall gw;
	
  public BlockSurveyorsRod (int i, int j, mod_GreatWall gw_){
      super(i, j, Material.rock);
      gw=gw_;
  }

	public void onBlockRemoval(World world, int i, int j, int k){
		if(gw.placedCoords!=null && gw.placedCoords[0]==i && gw.placedCoords[1]==j && gw.placedCoords[2]==k)
			gw.placedCoords=null;
  }

  public int idDropped(int i, Random random){
      return blockID;
  }
    
  public void onBlockAdded(World world, int i, int j, int k){
  	if(gw.placedCoords==null || gw.placedWorld!=world){
  		gw.placedCoords=new int[]{i,j,k};
  		gw.placedWorld=world;
  	}
  	else{
  		System.out.println("\nPrevious magicWall placed at "+gw.placedCoords[0]+" "+gw.placedCoords[1]+" "+gw.placedCoords[2]);
  		System.out.println("this magicWall placed at "+i+" "+j+" "+k);
  		gw.exploreThreads.add(new WorldGenSingleWall(gw, world, new Random(), new int[]{i,j,k}));
 
  	}
  }
  	
}