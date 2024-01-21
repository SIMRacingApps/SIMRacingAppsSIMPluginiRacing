/**
 * 
 */
package com.SIMRacingApps.SIMPlugins.iRacing.Gauges;

import com.SIMRacingApps.Car;
import com.SIMRacingApps.Data;
import com.SIMRacingApps.Server;
import com.SIMRacingApps.Track;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingCar;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingGauge;
import com.SIMRacingApps.SIMPlugins.iRacing.IODrivers.IODriver;

/**
 * Retrieves the Brake Bias. Some cars telemetry doesn't match the setup value, so it adjusts for it.
 * 
 * The adjustment can be turned off in the settings with "brake-bias-correction = N".
 * 
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2015 - 2024 Jeffrey Gilliam
 * @since 1.18
 * @license Apache License 2.0
 */
public class BrakeBias extends iRacingGauge {

    private double m_brakeBiasOffset = 0.0;
    private String m_sBrakeBiasSetup = "";
    private double m_brakeBias = 9999.0;
    
    public BrakeBias(String type, iRacingCar car, Track track, IODriver IODriver) {
        super(type, car, track, IODriver, "dcBrakeBias","%",null,null);
    }

    @Override public Data getValueCurrent(String UOM) {
        Data d = super.getValueCurrent(UOM);
        
        /*
         * brake-bias-correction              TRUE  TRUE   FALSE  FALSE
         * brake-bias-correction-{CARNAME}    TRUE  FALSE  TRUE   FALSE
         * 
         * -------------------------------    TRUE  FALSE  TRUE   FALSE
         */
        boolean brake_bias_correction = Server.getArg("brake-bias-correction", true);
        if (brake_bias_correction)
            brake_bias_correction = Server.getArg("brake-bias-correction-" + this.m_car.getName().getString().replace(' ', '_'),true);
        else
            brake_bias_correction = Server.getArg("brake-bias-correction-" + this.m_car.getName().getString().replace(' ', '_'),false);

        if (brake_bias_correction) {
            String sBrakeBiasSetup = m_IODriver.getSessionInfo().getString("CarSetup","Chassis","Front","FrontBrakeBias");
            if (sBrakeBiasSetup.isEmpty())
                sBrakeBiasSetup = m_IODriver.getSessionInfo().getString("CarSetup","Chassis","Front","BrakeBalanceBar");
            
            double brakeBias       = d.getDouble();
            
            //recalculate offset if something has changed
            if (!sBrakeBiasSetup.isEmpty()
            &&  !sBrakeBiasSetup.equals("0%")
            &&  (!sBrakeBiasSetup.equals(m_sBrakeBiasSetup)  //previous setup value is different
//                 ||
//                 Math.abs(m_brakeBias - brakeBias) > Server.getArg("brake-bias-max-adjustment-range", 12.0) //previous bias reading is greater than the maximum range
                )
            &&  //and you just entered the session
                (this.m_car.getStatus().getString().equals(Car.Status.INPITSTALL)
                 ||
                 this.m_car.getStatus().getString().equals(Car.Status.LEAVINGPITS)
                 ||
                 this.m_car.getStatus().getString().equals(Car.Status.ONPITROAD)
                 ||
                 this.m_car.getStatus().getString().equals(Car.Status.ONTRACK)
                )
            ) {
                try {
                    double brakeBiasSetup = Double.parseDouble(sBrakeBiasSetup.substring(0, sBrakeBiasSetup.length()-1)); //strip off percent sign

                    m_brakeBiasOffset = brakeBiasSetup - brakeBias;
                    Server.logger().fine(String.format("Calculating Brake Bias Offset: Setup Prev=%s Curr=%s, dcBrakeBias Prev=%f Curr=%f, Offset=%f", m_sBrakeBiasSetup,sBrakeBiasSetup,m_brakeBias,brakeBias,m_brakeBiasOffset));
                    m_sBrakeBiasSetup = sBrakeBiasSetup; //save setup value for detecting changes
                    m_brakeBias       = brakeBias;
                }
                catch (NumberFormatException e) {}
            }
        }
        
        d.setValue(d.getDouble() + m_brakeBiasOffset,d.getUOM());
        
        return this._getReturnValue(d, UOM);
    }
}
