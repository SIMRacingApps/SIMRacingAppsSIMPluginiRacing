package com.SIMRacingApps.SIMPlugins.iRacing.VarData;

import com.SIMRacingApps.Data;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingCar;
import com.SIMRacingApps.SIMPlugins.iRacing.IODrivers.IODriver;

/**
 * This tape reader conditionally returns the tape percentage based on the build
 * if prior to april 22, 2014 the tape was simply values from 0 to 10
 * after that, 0 - 100.
 */

public class VarDataDoubleTape extends VarDataDouble {
    private static final long serialVersionUID = -3624790472059612202L;

    public VarDataDoubleTape(IODriver IODriver, iRacingCar car) {
        super(IODriver,car,"dpQtape","%");
    }

    @Override
    public Object getValue(String name) {
        double pct = (Double)super.getValue(name);
        if (Double.isNaN(pct)) //in a 2014 build, not sure which, tape was omitted from the output on some cars
            this.setState(Data.State.OFF);
        else
        if (!m_IODriver.build_april_22_2014())
            pct = pct / 10.0;
        else
            pct = pct / 100.0;
        return pct;
    }
    
}
