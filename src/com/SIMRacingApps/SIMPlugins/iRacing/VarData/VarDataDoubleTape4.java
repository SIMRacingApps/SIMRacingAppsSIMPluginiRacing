package com.SIMRacingApps.SIMPlugins.iRacing.VarData;

import com.SIMRacingApps.SIMPlugins.iRacing.iRacingCar;
import com.SIMRacingApps.SIMPlugins.iRacing.IODrivers.IODriver;

/**
 * use this class if the tape value returns these 4 possible values, 0,100,200,300,400
 * it will convert them to percentage.
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2015 - 2017 Jeffrey Gilliam
 * @since 1.0
 * @license Apache License 2.0
 */

public class VarDataDoubleTape4 extends VarDataDouble {
    private static final long serialVersionUID = -3624790472059612202L;

    public VarDataDoubleTape4(IODriver IODriver, iRacingCar car) {
        super(IODriver,car,"dpQtape","%");
    }

    @Override
    public Object getValue(String name) {
        Double d = (Double)super.getValue(name);
        if (d == 400.0 || d == 4.0)
            d = 100.0;
        else
        if (d == 300.0 || d == 3.0)
            d = 75.0;
        else
        if (d == 200.0 || d == 2.0)
            d = 50.0;
        else
        if (d == 100.0 || d == 1.0)
            d = 25.0;
        else
            d = 0.0;
        setValue(name,d);
        return d;
    }
    
}
