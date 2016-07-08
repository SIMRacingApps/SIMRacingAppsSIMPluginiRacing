package com.SIMRacingApps.SIMPlugins.iRacing.VarData;

import com.SIMRacingApps.SIMPlugins.iRacing.iRacingCar;
import com.SIMRacingApps.SIMPlugins.iRacing.IODrivers.IODriver;

/**
 * This tape reader simply divides the value by 100 to normalize it to a percentage
 */

public class VarDataDoubleTapePct extends VarDataDouble {
    private static final long serialVersionUID = -3624790472059612202L;

    public VarDataDoubleTapePct(IODriver IODriver, iRacingCar car) {
        super(IODriver,car,"dpQtape","%");
    }

    @Override
    public Object getValue(String name) {
        double pct = (Double)super.getValue(name);
        pct = pct / 100.0;
        return pct;
    }
    
}
