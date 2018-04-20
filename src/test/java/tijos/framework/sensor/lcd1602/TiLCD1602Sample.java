package tijos.framework.sensor.lcd1602;

import java.io.IOException;
import java.util.Date;

import tijos.framework.devicecenter.TiI2CMaster;
import tijos.framework.util.Delay;

/**
 * LCD1602 Samples
 *
 */
public class TiLCD1602Sample
{
	public static void main(String[] args) {
		System.out.println("LCD 1602 Sample");

		byte[] heart = new byte[] { 0x0, 0xa, 0x1f, 0x1f, 0xe, 0x4, 0x0 }; // example sprite bitmap

		try {

			TiI2CMaster i2cmaster = TiI2CMaster.open(0);

			TiLCD1602 lcd = new TiLCD1602(i2cmaster, 0x3f);
			lcd.initialize();

			lcd.backlight();
			lcd.createChar(1, heart);

			while (true) {

				lcd.home(); // At column=0, row=0
				lcd.print("TiJOS");
				lcd.setCursor(0, 1);

				Date dateobj = new Date();

				lcd.print(dateobj.toString());

				lcd.setCursor(10, 0); // At column=10, row=0
				lcd.write(1);

				Delay.msDelay(500);
				lcd.setCursor(10, 0); // At column=10, row=0
				lcd.print(" "); // Wipe sprite

				Delay.msDelay(500);
			}

		} catch (IOException ex) {

			ex.printStackTrace();
		}

	}

}
