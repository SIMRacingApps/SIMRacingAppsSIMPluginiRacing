package com.SIMRacingApps.SIMPlugins.iRacing.VarData;

import com.SIMRacingApps.Data;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingCar;
import com.SIMRacingApps.SIMPlugins.iRacing.IODrivers.IODriver;

/**
 * Returns the speed of a car provided by iRacing 
 * If the car is not ME, then we have to calculate it based on time and distance traveled.
 */

public class VarDataDoubleSpeed extends VarDataDouble {
    private static final long serialVersionUID = 1464755072577475290L;
    
    private final int NUM_SPEED_SAMPLES     = 10;
    private double  m_speed                 = 0.0;
    private double  m_speed_sessionTime[]   = new double[NUM_SPEED_SAMPLES];
    private double  m_speed_percentage[]    = new double[NUM_SPEED_SAMPLES];
    private int     m_speed_index           = 0;
    
    /**
     * Class constructor
     * 
     * @param IODriver The instance of the iRacing driver to use
     * @param car      The car this instance refers to.
     */
    public VarDataDoubleSpeed(IODriver IODriver, iRacingCar car) {
        super(IODriver,car,"Speed","km/h");
    }

    /**
     * Returns the current speed calculated by the last call to onDataVersionChange().
     * 
     * @param name The name of the Data
     */
    @Override
    public Object getValue(String name) {
        double d = 0.0;

        if (m_car.isValid()) {
            d = m_speed;
            setState(Data.State.NORMAL);
        }
        else {
            setState(Data.State.OFF);
        }

        setValue(name,d);
        return d;
    }

    /**
     * This method calculates the speed of the car.
     * Each instance of this Class, must call this method every time the Data Version changes.
     * 
     * @param sessionTime         The time since the session started
     * @param lapCompletedPercent The percentage completed so far for the current lap
     * @param trackLength         The length of the track in the iRacing provided Unit of Measure
     */
    public void onDataVersionChange(double sessionTime,double lapCompletedPercent,double trackLength) {
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
                }
            }
        }
    }    
}
