package com.mycompany;

/**
 * Created by Ilya on 13.12.2016.
 */
public class Test
{
	@org.junit.Test
	public void testRegexp()
	{
		String s = "http://tdsc-prod.zaelab.com";
		String regexp = "(?i)^https?://tdsc-prod\\.[^/]+(|/.*|\\?.*)$";
		System.out.println(s.matches(regexp));
	}
}