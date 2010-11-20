/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ide.eclipse.adt.internal.editors.layout.gle2;

import com.android.ide.common.api.Rect;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

import junit.framework.TestCase;

public class SwtUtilsTest extends TestCase {

    public void testImageConvertNoAlpha() throws Exception {
        BufferedImage inImage = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics g = inImage.getGraphics();
        g.setColor(new Color(0xFF00FF00, true));  // green
        g.fillRect(0, 0, inImage.getWidth(), inImage.getHeight());
        g.dispose();

        Shell shell = new Shell();
        Display display = shell.getDisplay();

        Image outImage = SwtUtils.convertToSwt(display, inImage, false, -1);
        assertNotNull(outImage);

        ImageData outData = outImage.getImageData();
        assertEquals(inImage.getWidth(), outData.width);
        assertEquals(inImage.getHeight(), outData.height);
        assertNull(outData.alphaData);

        PaletteData inPalette  = SwtUtils.getAwtPaletteData(inImage.getType());
        PaletteData outPalette = outData.palette;

        for (int y = 0; y < outData.height; y++) {
            for (int x = 0; x < outData.width; x++) {
                // Note: we can't compare pixel directly as integers since convertToSwt() might
                // have changed the RGBA ordering depending on the platform (e.g. it will on
                // Windows.)
                RGB expected = outPalette.getRGB(outData.getPixel(x, y));
                RGB actual   = inPalette.getRGB( inImage.getRGB(  x, y));
                assertEquals(expected, actual);
            }
        }

        // Convert back to AWT and compare with original AWT image
        BufferedImage awtImage = SwtUtils.convertToAwt(outImage);
        assertNotNull(awtImage);

        // Both image have compatible RGB orderings
        assertEquals(BufferedImage.TYPE_INT_ARGB_PRE, inImage.getType());
        assertEquals(BufferedImage.TYPE_INT_ARGB,     awtImage.getType());

        for (int y = 0; y < outData.height; y++) {
            for (int x = 0; x < outData.width; x++) {
                // Note: we can compare pixels as integers since we just
                // asserted both images have the same color image type.
                assertEquals(inImage.getRGB(x, y), awtImage.getRGB(x, y));
            }
        }
    }

    public void testImageConvertGlobalAlpha() throws Exception {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics g = image.getGraphics();
        g.setColor(new Color(0xFF00FF00, true));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.dispose();

        Shell shell = new Shell();
        Display display = shell.getDisplay();
        Image swtImage = SwtUtils.convertToSwt(display, image, false, 128);
        assertNotNull(swtImage);
        ImageData data = swtImage.getImageData();
        assertEquals(image.getWidth(), data.width);
        assertEquals(image.getHeight(), data.height);
        assertNull(data.alphaData);
        assertEquals(128, data.alpha);
        for (int y = 0; y < data.height; y++) {
            for (int x = 0; x < data.width; x++) {
                assertEquals(image.getRGB(x, y) & 0xFFFFFF, data.getPixel(x, y));
            }
        }
    }

    public void testImageConvertAlpha() throws Exception {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics g = image.getGraphics();
        g.setColor(new Color(0xFF00FF00, true));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.dispose();

        Shell shell = new Shell();
        Display display = shell.getDisplay();
        Image swtImage = SwtUtils.convertToSwt(display, image, true, -1);
        assertNotNull(swtImage);
        ImageData data = swtImage.getImageData();
        assertEquals(image.getWidth(), data.width);
        assertEquals(image.getHeight(), data.height);
        for (int y = 0; y < data.height; y++) {
            for (int x = 0; x < data.width; x++) {
                assertEquals(image.getRGB(x, y) & 0xFFFFFF, data.getPixel(x, y));
                // Note: >> instead of >>> since we will compare with byte (a signed
                // number)
                assertEquals(image.getRGB(x, y) >> 24, data.alphaData[y * data.width + x]);
            }
        }
    }

    public void testImageConvertAlphaMultiplied() throws Exception {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics g = image.getGraphics();
        g.setColor(new Color(0xFF00FF00, true));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.dispose();

        Shell shell = new Shell();
        Display display = shell.getDisplay();
        Image swtImage = SwtUtils.convertToSwt(display, image, true, 32);
        assertNotNull(swtImage);
        ImageData data = swtImage.getImageData();
        assertEquals(image.getWidth(), data.width);
        assertEquals(image.getHeight(), data.height);
        for (int y = 0; y < data.height; y++) {
            for (int x = 0; x < data.width; x++) {
                assertEquals(image.getRGB(x, y) & 0xFFFFFF, data.getPixel(x, y));
                int expectedAlpha = (image.getRGB(x,y) >>> 24);
                byte expected = (byte)(expectedAlpha / 8);
                byte actual = data.alphaData[y * data.width + x];
                assertEquals(expected, actual);
            }
        }
    }

    public final void testSetRectangle() {
        Rect r = new Rect(1, 2, 3, 4);
        Rectangle r2 = new Rectangle(3, 4, 20, 30);
        SwtUtils.set(r, r2);

        assertEquals(3, r.x);
        assertEquals(4, r.y);
        assertEquals(20, r.w);
        assertEquals(30, r.h);
    }

    public final void testRectRectangle() {
        Rectangle r = new Rectangle(3, 4, 20, 30);
        Rect r2 = SwtUtils.toRect(r);

        assertEquals(3, r2.x);
        assertEquals(4, r2.y);
        assertEquals(20, r2.w);
        assertEquals(30, r2.h);
    }

}
