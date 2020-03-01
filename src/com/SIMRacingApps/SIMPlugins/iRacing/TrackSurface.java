package com.SIMRacingApps.SIMPlugins.iRacing;

import com.SIMRacingApps.SIMPlugins.iRacing.IODrivers.IODriver;

//enum irsdk_TrkLoc
/**
 * Copyright (c) 2013, iRacing.com Motorsport Simulations, LLC.
 * All rights reserved.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2015 - 2020 Jeffrey Gilliam
 * @since 1.0
 * @license Apache License 2.0
 */

public class TrackSurface {
    public final static int NotInWorld     = -1;
    public final static int OffTrack       = 0;
    public final static int InPitStall     = 1;
    public final static int AproachingPits = 2;
    public final static int OnTrack        = 3;
    public final static int OnPitRoad      = 99;    //not set by iRacing. I will use the OnPitRoad and CarIdxOnPitRoad to populate it
    
    public final static int getTrackSurface(IODriver IODriver,int carIdx,boolean isME) {
        int surfaceLocation = NotInWorld;
        if (carIdx != -1) {
            int onPitRoad  = -1;
            
//June 2016 build introduced this var. I assume it works for when you are not in the car
//it will return your teammates position. I will hold off until I have time to test it.
//            if (IODriver.getVarHeaders().getVarHeader("PlayerTrackSurface") != null) {
//                surfaceLocation = IODriver.getVars().getInteger("PlayerTrackSurface");
//            }
//            else {
                //See if we have surface information indexed vars. IBT files do not have these
                try {
                    surfaceLocation = IODriver.getVars().getInteger("CarIdxTrackSurface",carIdx);
                }
                catch (IndexOutOfBoundsException e) {
                    //since the array is not valid, then use the old fields if the car is ME
                }
//            }
            
            try {
                onPitRoad       = IODriver.getVars().getInteger("CarIdxOnPitRoad",carIdx);
            }
            catch (IndexOutOfBoundsException e) {
                //since the array is not valid, then use the old fields if the car is ME
            }
            
            if (onPitRoad == -1 && isME) {
                onPitRoad  = IODriver.getVars().getInteger("OnPitRoad");
            }
            
            //now update the surface with on pit road, if we are
            //but don't hide the fact we blinked.
            if (onPitRoad == 1 && surfaceLocation != InPitStall && surfaceLocation != NotInWorld)
                surfaceLocation = OnPitRoad;
        }
        
        return surfaceLocation;
    }
    
    public static String toString(int surfaceLocation) {
        String l = new Integer(surfaceLocation).toString();
        if (surfaceLocation == NotInWorld)
            l = "NotInWorld";
        else
        if (surfaceLocation == OffTrack)
            l = "OffTrack";
        else
        if (surfaceLocation == InPitStall)
            l = "InPitStall";
        else
        if (surfaceLocation == AproachingPits)
            l = "ApproachingPits";
        else
        if (surfaceLocation == OnTrack)
            l = "OnTrack";
        else
        if (surfaceLocation == OnPitRoad)
            l = "OnPitRoad";
        
        return l;
    }
    public static String toString(String surfaceLocation) {
        try {
            return toString(Integer.parseInt(surfaceLocation));
        }
        catch (Exception e) {}
        return surfaceLocation;
    }
}
