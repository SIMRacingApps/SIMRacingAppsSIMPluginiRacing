/**
 * 
 */
package com.SIMRacingApps.SIMPlugins.iRacing.Gauges;

import com.SIMRacingApps.Data;
import com.SIMRacingApps.Track;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingCar;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingGauge;
import com.SIMRacingApps.SIMPlugins.iRacing.IODrivers.IODriver;
import com.SIMRacingApps.Util.State;

/**
 * Returns the speed of a car provided by iRacing 
 * If the car is not ME, then we have to calculate it based on time and distance traveled.
 * 
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2017 Jeffrey Gilliam
 * @since 1.5
 * @license Apache License 2.0
 */
public class Speedometer extends iRacingGauge {
    private final double SPEED_FACTOR       = 1.0;
    private final int NUM_SPEED_SAMPLES     = 3;
    private double  m_speed                 = 0.0;
    private double  m_speed_sessionTime[]   = new double[NUM_SPEED_SAMPLES];
    private double  m_speed_percentage[]    = new double[NUM_SPEED_SAMPLES];
    private int     m_speed_index           = 0;

    public Speedometer(String type, iRacingCar car, Track track,
            IODriver IODriver, String varName, String defaultUOM) {
        super(type, car, track, IODriver, varName, defaultUOM, null, null);
        
        //Add these states for the Speedometer which has to be calculated from pit road speed limit.
        //Therefore, it cannot be set statically in the .json files.
        //convert the track UOM to the gauges UOM
        //I used to floor it and round it, but that produced problems with accuracy.
        //This code cannot make any assumptions about the error the track code may return.
        //In my test file, the speed limit is a published 45mph, but the track code returns 44.7.
        //My point is, the track code should round it up or floor it, not this code.
        double PitRoadSpeedLimit = track.getPitSpeedLimit(this.m_UOM).getDouble();

        //double WayOverPitSpeed     = 1.10;
        double WayOverPitSpeed     = (PitRoadSpeedLimit + (this.m_UOM.equals("mph") ? 15.0 : 25.0)) / PitRoadSpeedLimit;
        double OverPitSpeed        = (PitRoadSpeedLimit + 0.8) / PitRoadSpeedLimit;
        double PitSpeed            = (PitRoadSpeedLimit - 0.5) / PitRoadSpeedLimit;
        
        //these percentages correspond to the percentages used by the shift lights
        //This is really not a good way to do this
        //TODO: Redesign how this range is calculated to independent of the shift lights code in the client.
        double ApproachingPitSpeed = PitSpeed - (7*.012) - (7*.006);

        _addStateRange("WAYOVERLIMIT", 		PitRoadSpeedLimit * WayOverPitSpeed, 	Double.MAX_VALUE,                   this.m_UOM);
        _addStateRange("OVERLIMIT", 		PitRoadSpeedLimit * OverPitSpeed, 	 	PitRoadSpeedLimit * WayOverPitSpeed,this.m_UOM);
        _addStateRange("LIMIT", 			PitRoadSpeedLimit * PitSpeed,        	PitRoadSpeedLimit * OverPitSpeed,   this.m_UOM);
        _addStateRange("APPROACHINGLIMIT", 	PitRoadSpeedLimit * ApproachingPitSpeed,PitRoadSpeedLimit * PitSpeed,       this.m_UOM);
    }

    @Override
    public Data getValueCurrent(String UOM) { 
        Data d = super.getValueCurrent(UOM);
        
        if (m_car.isValid()) {
            d.setValue(m_speed,m_iRacingUOM,Data.State.NORMAL);
        }
        else {
            d.setState(Data.State.OFF);
        }
        
        return this._getReturnValue(d, UOM);
    }
    
    @Override
    public void onDataVersionChange(State state,int currentLap,double sessionTime,double lapCompletedPercent,double trackLength) {
        super.onDataVersionChange(state, currentLap, sessionTime, lapCompletedPercent, trackLength);
        
        if (m_car.isME()) {
            m_speed = m_IODriver.getVars().getDouble("Speed");
        }
        else {
            m_speed = 0.0;
    
            //for the other cars, take samples and calculate the amount of time between the them and use that against the track length
            m_speed_sessionTime[m_speed_index] = sessionTime;
            m_speed_percentage[m_speed_index]  = lapCompletedPercent;
            int index                          = m_speed_index;
            m_speed_index                      = (m_speed_index + 1) % NUM_SPEED_SAMPLES;
    
            //if we have enough samples
            if (m_speed_sessionTime[index] > 0.0 && m_speed_sessionTime[m_speed_index] > 0.0) {
                double start        = m_speed_percentage[m_speed_index];
                double distanceTime = sessionTime - m_speed_sessionTime[m_speed_index];
    
                if (distanceTime > 0.0) {
                    double distancePercent;
                    //if start is ahead of current compensate
                    if (start > lapCompletedPercent) {
                        distancePercent = lapCompletedPercent + (1.0 - start);
                    }
                    else {
                        distancePercent = lapCompletedPercent - start;
                    }
    
                    double meters = (trackLength * 1000 * distancePercent);
                    m_speed       = meters / distanceTime;
                    if (m_speed > 500.0 || m_speed < 1.0)
                        m_speed = 0.0;
                    else
                        m_speed *= SPEED_FACTOR; 
                }
            }
        }
    }    
}
