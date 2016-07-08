package com.SIMRacingApps.SIMPlugins.iRacing.VarData;

import com.SIMRacingApps.SIMPlugins.iRacing.iRacingCar;
import com.SIMRacingApps.SIMPlugins.iRacing.IODrivers.IODriver;

/**
 * Returns the number of fast repairs you have remaining. 
 */

public class VarDataDoubleFastRepairs extends VarDataDouble {

    /**
     * Class constructor
     * 
     * @param IODriver The instance of the iRacing driver to use
     * @param car      The car this instance refers to.
     */
    public VarDataDoubleFastRepairs(IODriver IODriver, iRacingCar car) {
        super(IODriver,car,"FastRepairsRemaining",""); //TODO: Just a guess at what David will name Fast Repairs Remaining
    }

    /**
     * Returns the current Fast Repairs value.
     * 
     * @param name The name of the Data
     */
    @Override
    public Object getValue(String name) {
        if (m_varHeader != null)
            return super.getValue();
        return 0.0;
    }
}
