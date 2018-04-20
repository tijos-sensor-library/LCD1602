package tijos.framework.sensor.lcd1602;

import java.io.IOException;
import tijos.framework.devicecenter.TiI2CMaster;
import tijos.framework.util.Delay;

/**
 * LCD1602 I2C driver for TiJOS, witch is based on https://github.com/agnunez/ESP8266-I2C-LCD1602
 * 
 */

// When the display powers up, it is configured as follows:
//
// 1. Display clear
// 2. Function set:
// DL = 1; 8-bit interface data
// N = 0; 1-line display
// F = 0; 5x8 dot character font
// 3. Display on/off control:
// D = 0; Display off
// C = 0; Cursor off
// B = 0; Blinking off
// 4. Entry mode set:
// I/D = 1; Increment by 1
// S = 0; No shift


public class TiLCD1602 {

	// commands
	public static final int LCD_CLEARDISPLAY = 0x01;
	public static final int LCD_RETURNHOME = 0x02;
	public static final int LCD_ENTRYMODESET = 0x04;
	public static final int LCD_DISPLAYCONTROL = 0x08;
	public static final int LCD_CURSORSHIFT = 0x10;
	public static final int LCD_FUNCTIONSET = 0x20;
	public static final int LCD_SETCGRAMADDR = 0x40;
	public static final int LCD_SETDDRAMADDR = 0x80;

	// flags for display entry mode
	public static final int LCD_ENTRYRIGHT = 0x00;
	public static final int LCD_ENTRYLEFT = 0x02;
	public static final int LCD_ENTRYSHIFTINCREMENT = 0x01;
	public static final int LCD_ENTRYSHIFTDECREMENT = 0x00;

	// flags for display on/off control
	public static final int LCD_DISPLAYON = 0x04;
	public static final int LCD_DISPLAYOFF = 0x00;
	public static final int LCD_CURSORON = 0x02;
	public static final int LCD_CURSOROFF = 0x00;
	public static final int LCD_BLINKON = 0x01;
	public static final int LCD_BLINKOFF = 0x00;

	// flags for display/cursor shift
	public static final int LCD_DISPLAYMOVE = 0x08;
	public static final int LCD_CURSORMOVE = 0x00;
	public static final int LCD_MOVERIGHT = 0x04;
	public static final int LCD_MOVELEFT = 0x00;

	// flags for function set
	public static final int LCD_8BITMODE = 0x10;
	public static final int LCD_4BITMODE = 0x00;
	public static final int LCD_2LINE = 0x08;
	public static final int LCD_1LINE = 0x00;
	public static final int LCD_5x10DOTS = 0x04;
	public static final int LCD_5x8DOTS = 0x00;

	// flags for back light control
	public static final int LCD_BACKLIGHT = 0x08;
	public static final int LCD_NOBACKLIGHT = 0x00;

	public static final int En = 4; // Enable bit
	public static final int Rw = 2; // Read/Write bit
	public static final int Rs = 1; // Register select bit

	int _i2cAddress;
	byte _displayfunction;
	byte _displaycontrol;
	byte _displaymode;
	int _cols;
	int _rows;
	int _charsize;

	int _backlightval;

	byte[] data = new byte[1];
	/**
	 * TiI2CMaster object
	 */
	private TiI2CMaster i2cmObj;

	public TiLCD1602(TiI2CMaster i2cMaster, int i2cAddress) {

		this.i2cmObj = i2cMaster;
		_i2cAddress = i2cAddress;
		_cols = 16;
		_rows = 2;
		_charsize = LCD_5x8DOTS;
		_backlightval = LCD_BACKLIGHT;
	}

	public void setCharSize(int charsize) {
		this._charsize = charsize;
	}

	public void initialize() throws IOException {
		_displayfunction = LCD_4BITMODE | LCD_1LINE | LCD_5x8DOTS;

		if (_rows > 1) {
			_displayfunction |= LCD_2LINE;
		}

		// for some 1 line displays you can select a 10 pixel high font
		if ((_charsize != 0) && (_rows == 1)) {
			_displayfunction |= LCD_5x10DOTS;
		}

		// SEE PAGE 45/46 FOR INITIALIZATION SPECIFICATION!
		// according to datasheet, we need at least 40ms after power rises above 2.7V
		// before sending commands. Arduino can turn on way befer 4.5V so we'll wait 50
		Delay.msDelay(50);

		// Now we pull both RS and R/W low to begin commands
		expanderWrite(_backlightval); // reset expanderand turn backlight off (Bit 8 =1)
		Delay.msDelay(1000);

		// put the LCD into 4 bit mode
		// this is according to the hitachi HD44780 datasheet
		// figure 24, pg 46

		// we start in 8bit mode, try to set 4 bit mode
		write4bits(0x03 << 4);
		Delay.msDelay(4); // wait min 4.1ms

		// second try
		write4bits(0x03 << 4);
		Delay.msDelay(4); // wait min 4.1ms

		// third go!
		write4bits(0x03 << 4);
		Delay.usDelay(150);

		// finally, set to 4-bit interface
		write4bits(0x02 << 4);

		// set # lines, font size, etc.
		command(LCD_FUNCTIONSET | _displayfunction);

		// turn the display on with no cursor or blinking default
		_displaycontrol = LCD_DISPLAYON | LCD_CURSOROFF | LCD_BLINKOFF;
		display();

		// clear it off
		clear();

		// Initialize to default text direction (for roman languages)
		_displaymode = LCD_ENTRYLEFT | LCD_ENTRYSHIFTDECREMENT;

		// set the entry mode
		command(LCD_ENTRYMODESET | _displaymode);

		home();
	}

	/**
	 * clear display, set cursor position to zero
	 * 
	 * @throws IOException
	 */
	public void clear() throws IOException {
		command(LCD_CLEARDISPLAY);
		Delay.msDelay(2);
	}

	/**
	 * set cursor position to zero
	 * 
	 * @throws IOException
	 */
	public void home() throws IOException {
		command(LCD_RETURNHOME);
		Delay.msDelay(2);
	}

	public void setCursor(int col, int row) throws IOException {
		int row_offsets[] = { 0x00, 0x40, 0x14, 0x54 };
		if (row > _rows) {
			row = _rows - 1; // we count rows starting w/0
		}
		command(LCD_SETDDRAMADDR | (col + row_offsets[row]));
	}

	/**
	 * Turn the display off (quickly)
	 * 
	 * @throws IOException
	 */
	public void noDisplay() throws IOException {
		_displaycontrol &= ~LCD_DISPLAYON;
		command(LCD_DISPLAYCONTROL | _displaycontrol);
	}

	/**
	 * Turn the display on (quickly)
	 * 
	 * @throws IOException
	 */
	public void display() throws IOException {
		_displaycontrol |= LCD_DISPLAYON;
		command(LCD_DISPLAYCONTROL | _displaycontrol);
	}

	/**
	 * Turns the underline cursor off
	 * 
	 * @throws IOException
	 */
	public void noCursor() throws IOException {
		_displaycontrol &= ~LCD_CURSORON;
		command(LCD_DISPLAYCONTROL | _displaycontrol);
	}

	/**
	 * Turns the underline cursor on
	 * 
	 * @throws IOException
	 */
	public void cursor() throws IOException {
		_displaycontrol |= LCD_CURSORON;
		command(LCD_DISPLAYCONTROL | _displaycontrol);
	}

	/**
	 * Turn off the blinking cursor
	 * 
	 * @throws IOException
	 */
	public void noBlink() throws IOException {
		_displaycontrol &= ~LCD_BLINKON;
		command(LCD_DISPLAYCONTROL | _displaycontrol);
	}

	/**
	 * Turn on the blinking cursor
	 * 
	 * @throws IOException
	 */
	public void blink() throws IOException {
		_displaycontrol |= LCD_BLINKON;
		command(LCD_DISPLAYCONTROL | _displaycontrol);
	}

	/**
	 * These commands scroll the display without changing the RAM
	 * 
	 * @throws IOException
	 */
	public void scrollDisplayLeft() throws IOException {
		command(LCD_CURSORSHIFT | LCD_DISPLAYMOVE | LCD_MOVELEFT);
	}

	/**
	 * @see scrollDisplayLeft
	 * @throws IOException
	 */
	public void scrollDisplayRight() throws IOException {
		command(LCD_CURSORSHIFT | LCD_DISPLAYMOVE | LCD_MOVERIGHT);
	}

	/**
	 * This is for text that flows Left to Right
	 * 
	 * @throws IOException
	 */
	public void leftToRight() throws IOException {
		_displaymode |= LCD_ENTRYLEFT;
		command(LCD_ENTRYMODESET | _displaymode);
	}

	/**
	 * This is for text that flows Right to Left
	 * 
	 * @throws IOException
	 */
	public void rightToLeft() throws IOException {
		_displaymode &= ~LCD_ENTRYLEFT;
		command(LCD_ENTRYMODESET | _displaymode);
	}

	/**
	 * This will 'right justify' text from the cursor
	 * 
	 * @throws IOException
	 */
	public void autoscroll() throws IOException {
		_displaymode |= LCD_ENTRYSHIFTINCREMENT;
		command(LCD_ENTRYMODESET | _displaymode);
	}

	/**
	 * This will 'left justify' text from the cursor
	 * 
	 * @throws IOException
	 */
	public void noAutoscroll() throws IOException {
		_displaymode &= ~LCD_ENTRYSHIFTINCREMENT;
		command(LCD_ENTRYMODESET | _displaymode);
	}

	/**
	 * Allows us to fill the first 8 CGRAM locations with custom characters
	 * 
	 * @param location
	 * @param charmap
	 * @throws IOException
	 */
	public void createChar(int location, byte charmap[]) throws IOException {
		location &= 0x7; // we only have 8 locations 0-7
		command(LCD_SETCGRAMADDR | (location << 3));
		for (int i = 0; i < charmap.length; i++) {
			write(charmap[i]);
		}
	}

	/**
	 * Turn the (optional) backlight off
	 * 
	 * @throws IOException
	 */
	public void noBacklight() throws IOException {
		_backlightval = LCD_NOBACKLIGHT;
		expanderWrite(0);
	}

	/**
	 * Turn the (optional) backlight on
	 * 
	 * @throws IOException
	 */
	public void backlight() throws IOException {
		_backlightval = LCD_BACKLIGHT;
		expanderWrite(0);
	}

	/**
	 * Print string on the screen
	 * 
	 * @param s
	 * @throws IOException
	 */
	public void print(String s) throws IOException {
		for (int i = 0; i < s.length(); i++) {
			int v = s.charAt(i);
			this.write(v);
		}
	}

	/**
	 * write value to the current position
	 * 
	 * @param value
	 * @throws IOException
	 */
	public void write(int value) throws IOException {
		send(value, Rs);
	}

	/***********
	 * mid level commands, for sending data/cmds
	 */
	void command(int value) throws IOException {
		send(value, 0);
	}

	/************
	 * low level data pushing commands
	 **********/

	// write either command or data
	void send(int value, int mode) throws IOException {
		byte highnib = (byte) (value & 0xf0);
		byte lownib = (byte) ((value << 4) & 0xf0);
		write4bits((highnib) | mode);
		write4bits((lownib) | mode);
	}

	void write4bits(int value) throws IOException {
		expanderWrite(value);
		pulseEnable(value);
	}

	void expanderWrite(int _data) throws IOException {

		data[0] = (byte) (_data | _backlightval);
		this.i2cmObj.write(_i2cAddress, data, 0, 1);

	}

	void pulseEnable(int _data) throws IOException {
		expanderWrite(_data | En); // En high
		// enable pulse must be >450ns
		Delay.usDelay(1);

		expanderWrite(_data & ~En); // En low
		Delay.usDelay(50); // commands need > 37us to settle
	}

	void load_custom_character(int char_num, byte[] rows) throws IOException {
		createChar(char_num, rows);
	}

	void setBacklight(int new_val) throws IOException {
		if (new_val > 0) {
			backlight(); // turn backlight on
		} else {
			noBacklight(); // turn backlight off
		}
	}

}
