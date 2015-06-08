/*
 * Copyright 2005 Joe Walker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package qz.utils;

import java.awt.Color;

/**
 * Utilities for working with colors.
 * @author Joe Walker [joe at getahead dot ltd dot uk]
 */
public class ColorUtilities
{
    
    public static Color DEFAULT_COLOR = Color.WHITE;
    /**
     * Decode an HTML color string like '#F567BA;' into a {@link Color}
     * @param colorString The string to decode
     * @return The decoded color, or White if it cannot be parsed
     */
    public static Color decodeHtmlColorString(String colorString)
    {
        Color color;
    
        if (colorString.startsWith("#"))
        {
            colorString = colorString.substring(1);
        }
        if (colorString.endsWith(";"))
        {
            colorString = colorString.substring(0, colorString.length() - 1);
        }
    
        int red, green, blue;
        switch (colorString.length())
        {
        case 6:
            red = Integer.parseInt(colorString.substring(0, 2), 16);
            green = Integer.parseInt(colorString.substring(2, 4), 16);
            blue = Integer.parseInt(colorString.substring(4, 6), 16);
            color = new Color(red, green, blue);
            break;
        case 3:
            red = Integer.parseInt(colorString.substring(0, 1), 16);
            green = Integer.parseInt(colorString.substring(1, 2), 16);
            blue = Integer.parseInt(colorString.substring(2, 3), 16);
            color = new Color(red, green, blue);
            break;
        case 1:
            red = green = blue = Integer.parseInt(colorString.substring(0, 1), 16);
            color = new Color(red, green, blue);
            break;
        default:
            return DEFAULT_COLOR;
        }
        return color;
    }
}