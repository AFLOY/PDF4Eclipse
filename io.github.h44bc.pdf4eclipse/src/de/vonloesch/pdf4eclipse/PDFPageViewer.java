/*******************************************************************************
 * Copyright (c) 2011 Boris von Loesch.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Boris von Loesch - initial API and implementation
 ******************************************************************************/
package de.vonloesch.pdf4eclipse;

import java.awt.Image;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.geom.Rectangle2D.Double;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.DirectColorModel;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.IPreferenceChangeListener;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.PreferenceChangeEvent;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;

import org.eclipse.swt.custom.ScrolledComposite;
import de.vonloesch.pdf4eclipse.editors.PDFEditor;
import de.vonloesch.pdf4eclipse.editors.handlers.ToggleLinkHighlightHandler;
import de.vonloesch.pdf4eclipse.model.IPDFFile;
import de.vonloesch.pdf4eclipse.model.IPDFPage;
import de.vonloesch.pdf4eclipse.model.IPDFLinkAnnotation;
import de.vonloesch.pdf4eclipse.preferences.PreferenceConstants;


/**
 * SWT Canvas which shows a whole pdf-page. It also handles click on links.
 * Since the pdf library returns an awt BufferedImage, we need to convert it
 * to an SWT image. This was avoided in {@link PDFPageViewerAWT}.
 * 
 * @author Boris von Loesch
 *
 */
public class PDFPageViewer extends Canvas implements PaintListener, IPreferenceChangeListener{
    /** The image of the rendered PDF page being displayed */
    private Image currentImage;
    
    /** The current PDFPage that was rendered into currentImage */
    public IPDFPage currentPage;
    /** the current transform from device space to page space */
    AffineTransform currentXform;
    /** The horizontal offset of the image from the left edge of the panel */
    int offx;
    /** The vertical offset of the image from the top of the panel */
    int offy;
    
    private boolean highlightLinks;
    private Rectangle2D highlight;
    
    private Display display;
    
    private float zoomFactor;
    
    private PDFEditor editor;
    private boolean centerPage;
    private boolean continuousMode;

    // Continuous mode layout data
    private int[] pageOffsets;
    private int[] pageHeights;
    private int totalHeight;
    private int maxWidth;
    private java.util.Map<Integer, org.eclipse.swt.graphics.Image> pageImageCache = new java.util.HashMap<>();
    private java.util.Map<Integer, Float> pageImageZoomCache = new java.util.HashMap<>();
    private java.util.Map<Integer, IPDFPage> renderedPageCache = new java.util.HashMap<>();
    private java.util.Map<Integer, Integer> retryCounts = new java.util.HashMap<>();
    private org.eclipse.swt.widgets.Listener parentResizeListener;
    private float lastZoomFactor = -1f;
    
    //private org.eclipse.swt.graphics.Image swtImage;

    /**
     * Create a new PagePanel.
     */
    public PDFPageViewer(Composite parent, final PDFEditor editor) {
        //super(parent, SWT.NO_BACKGROUND|SWT.NO_REDRAW_RESIZE);
    	//super(parent, SWT.EMBEDDED | SWT.NO_BACKGROUND | SWT.NO_REDRAW_RESIZE);
    	super(parent, SWT.NO_BACKGROUND | SWT.NO_MERGE_PAINTS | SWT.DOUBLE_BUFFERED);
    	this.editor = editor;

    	this.addMouseListener(new MouseListener() {
			
			@Override
			public void mouseUp(org.eclipse.swt.events.MouseEvent e) {
			}
			
			@Override
			public void mouseDown(org.eclipse.swt.events.MouseEvent e) {
				if (e.button != 1) return;
				
				int cx = e.x;
				int cy = e.y;
				
				int pageNr = currentPage != null ? currentPage.getPageNumber() : 1;
				if (continuousMode) {
					pageNr = getPageNumberForY(cy);
				}
				
				final IPDFPage page = renderedPageCache.containsKey(pageNr) ? renderedPageCache.get(pageNr) : editor.getPDFFile().getPage(pageNr);
				IPDFLinkAnnotation[] annos = page.getAnnotations();
				
				int pox = 0;
				int poy = 0;
				if (continuousMode) {
					poy = (pageOffsets != null && pageOffsets.length >= pageNr) ? pageOffsets[pageNr - 1] : 0;
					if (centerPage) {
						pox = (getSize().x - Math.round(zoomFactor * page.getWidth())) / 2;
					}
				} else {
					if (centerPage) {
						pox = offx;
						poy = offy;
					}
				}
				
				for (final IPDFLinkAnnotation a : annos) {
					Rectangle2D r = page.pdf2ImageCoordinates(a.getPosition());
					if (r.contains(cx - pox, cy - poy)) {
						if (a.getDestination() != null) {	
							Display.getDefault().asyncExec(new Runnable() {
								@Override
								public void run() {
									currentPage = page;
									editor.gotoAction(a.getDestination());
								}
							});
							return;
						}
					}
				}
			}
			
			@Override
			public void mouseDoubleClick(org.eclipse.swt.events.MouseEvent e) {
				if (e.button != 1) return;
				
				int cx = e.x;
				int cy = e.y;
				
				int pageNr = currentPage != null ? currentPage.getPageNumber() : 1;
				if (continuousMode) {
					pageNr = getPageNumberForY(cy);
				}
				
				final int targetPageNr = pageNr;
				final IPDFPage page = renderedPageCache.containsKey(targetPageNr) ? renderedPageCache.get(targetPageNr) : editor.getPDFFile().getPage(targetPageNr);
				
				int pox = 0;
				int poy = 0;
				if (continuousMode) {
					poy = (pageOffsets != null && pageOffsets.length >= targetPageNr) ? pageOffsets[targetPageNr - 1] : 0;
					if (centerPage) {
						pox = (getSize().x - Math.round(zoomFactor * page.getWidth())) / 2;
					}
				} else {
					if (centerPage) {
						pox = offx;
						poy = offy;
					}
				}
				
				final Rectangle2D r = page.image2PdfCoordinates(new java.awt.Rectangle(cx - pox, cy - poy, 1, 1));

				if (r != null) {
					Display.getDefault().asyncExec(new Runnable() {
						@Override
						public void run() {
							currentPage = page;
							editor.reverseSearch(r.getX(), page.getHeight() - r.getY());
						}
					});
				}
			}
		});


    	display = parent.getDisplay();
        setSize(800, 600);
        zoomFactor = 1.f;
        this.addPaintListener(this);
        
        IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(Activator.PLUGIN_ID);
		prefs.addPreferenceChangeListener(this);
		
		highlightLinks = prefs.getBoolean(ToggleLinkHighlightHandler.PREF_LINKHIGHTLIGHT_ID, true);
		centerPage = prefs.getBoolean(PreferenceConstants.PREF_CENTER_PAGE, true);
		continuousMode = prefs.getBoolean(PreferenceConstants.PREF_CONTINUOUS_MODE, false);

    	if (parent instanceof ScrolledComposite) {
    		parent.addListener(SWT.Resize, new org.eclipse.swt.widgets.Listener() {
				@Override
				public void handleEvent(org.eclipse.swt.widgets.Event event) {
					if (!isDisposed() && getPage() != null) {
						if (continuousMode) {
							updateCanvasSize(maxWidth, totalHeight);
						} else {
							int idealW = Math.round(zoomFactor * getPage().getWidth());
							int idealH = Math.round(zoomFactor * getPage().getHeight());
							updateCanvasSize(idealW, idealH);
						}
						redraw();
					}
				}
			});
    	}
    }

    
    /**
     * Converts a buffered image to SWT <code>ImageData</code>.
     *
     * @param bufferedImage  the buffered image (<code>null</code> not
     *         permitted).
     *
     * @return The image data.
     */
    public static ImageData convertToSWT(BufferedImage bufferedImage) {
        if (bufferedImage.getType() == BufferedImage.TYPE_4BYTE_ABGR) {
            java.awt.image.SampleModel sm = bufferedImage.getSampleModel();
            if (sm instanceof java.awt.image.ComponentSampleModel) {
                java.awt.image.ComponentSampleModel sampleModel = (java.awt.image.ComponentSampleModel) sm;
                int scanlineStride = sampleModel.getScanlineStride();
                int pixelStride = sampleModel.getPixelStride();
                int width = bufferedImage.getWidth();
                
                if (scanlineStride == width * pixelStride) {
                	byte[] datas =
                			((DataBufferByte) bufferedImage.getRaster()
                				.getDataBuffer())
                				.getData();
                	ImageData data = new ImageData(width,
                            bufferedImage.getHeight(), 32,
                            new PaletteData(0x000000FF, 0x0000FF00, 0x00FF0000));
                	data.data = datas;
                	return data;
                }
            }
            // Safe fallback when scanlineStride does not match width * pixelStride (e.g. sub-images)
            int width = bufferedImage.getWidth();
            int height = bufferedImage.getHeight();
            ImageData data = new ImageData(width, height, 32,
                    new PaletteData(0x000000FF, 0x0000FF00, 0x00FF0000));
            int[] rgbs = new int[width];
            for (int y = 0; y < height; y++) {
                bufferedImage.getRGB(0, y, width, 1, rgbs, 0, width);
                data.setPixels(0, y, width, rgbs, 0);
            }
            return data;
        }

    	if (bufferedImage.getColorModel() instanceof DirectColorModel) {
            DirectColorModel colorModel
                    = (DirectColorModel) bufferedImage.getColorModel();
            PaletteData palette = new PaletteData(colorModel.getRedMask(),
                    colorModel.getGreenMask(), colorModel.getBlueMask());
            ImageData data = null;
            if (bufferedImage.getType() == BufferedImage.TYPE_INT_ARGB) {
            	data = new ImageData(bufferedImage.getWidth(),
                        bufferedImage.getHeight(), colorModel.getPixelSize(),
                        palette);
            	//We get this type from PDFPage
            	int[] rbgs = new int[data.width];
                for (int y = 0; y < data.height; y += 1) {
                	bufferedImage.getRGB(0, y, data.width, 1, rbgs, 0, data.width);
                	data.setPixels(0, y, data.width, rbgs, 0);
                }
            }
            else if (bufferedImage.getType() == BufferedImage.TYPE_INT_RGB){
            	data = new ImageData(bufferedImage.getWidth(),
                        bufferedImage.getHeight(), colorModel.getPixelSize(),
                        palette);
            	int[] rbgs = new int[data.width];
                for (int y = 0; y < data.height; y += 1) {
                	bufferedImage.getRGB(0, y, data.width, 1, rbgs, 0, data.width);
                	data.setPixels(0, y, data.width, rbgs, 0);
                }
            }
            return data;
        }
        else if (bufferedImage.getColorModel() instanceof IndexColorModel) {
            IndexColorModel colorModel = (IndexColorModel)
                    bufferedImage.getColorModel();
            int size = colorModel.getMapSize();
            byte[] reds = new byte[size];
            byte[] greens = new byte[size];
            byte[] blues = new byte[size];
            colorModel.getReds(reds);
            colorModel.getGreens(greens);
            colorModel.getBlues(blues);
            RGB[] rgbs = new RGB[size];
            for (int i = 0; i < rgbs.length; i++) {
                rgbs[i] = new RGB(reds[i] & 0xFF, greens[i] & 0xFF,
                        blues[i] & 0xFF);
            }
            PaletteData palette = new PaletteData(rgbs);
            ImageData data = new ImageData(bufferedImage.getWidth(),
                    bufferedImage.getHeight(), colorModel.getPixelSize(),
                    palette);
            data.transparentPixel = colorModel.getTransparentPixel();
            WritableRaster raster = bufferedImage.getRaster();
            int[] pixelArray = new int[data.width];
            for (int y = 0; y < data.height; y++) {
                raster.getPixels(0, y, data.width, 1, pixelArray);
                data.setPixels(0, y, data.width, pixelArray, 0);
            }
            return data;
        }
        return null;
    }

    /**
     * Highlights the rectangle given by the upper left and lower right 
     * coordinates. The highlight is visible after the next redraw.
     * @param x
     * @param y
     * @param x2
     * @param y2
     */
    public void highlight(double x, double y, double x2, double y2) {
    	Rectangle2D r = new Double(x, currentPage.getHeight() - y2, x2-x, y2 - y);
    	IPDFPage page = renderedPageCache.containsKey(currentPage.getPageNumber()) ? renderedPageCache.get(currentPage.getPageNumber()) : currentPage;
    	highlight = convertPDF2ImageCoord(r, page);
    }

    /**
     * Stop the generation of any previous page, and draw the new one.
     * @param page the PDFPage to draw.
     */
    public void showPage(IPDFPage page) {
    	currentPage = page;
    	highlight = null;

     	if (continuousMode) {
    		if (zoomFactor != lastZoomFactor) {
    			lastZoomFactor = zoomFactor;
    		}
    		calculateLayout();
    		updateCanvasSize(maxWidth, totalHeight);
    		ScrolledComposite sc = (ScrolledComposite) getParent();
    		if (pageOffsets != null && pageOffsets.length >= page.getPageNumber()) {
    			int py = pageOffsets[page.getPageNumber() - 1];
    			sc.setOrigin(sc.getOrigin().x, py);
    		}
    	} else {
    		lastZoomFactor = zoomFactor;
    		int newW = Math.round(zoomFactor*page.getWidth());
    		int newH = Math.round(zoomFactor*page.getHeight());
    		updateCanvasSize(newW, newH);
    	}
    	redraw();
    }

    private Rectangle getRectangle(Rectangle2D r) {
    	return new Rectangle((int)Math.round(r.getX()), (int)Math.round(r.getY()), (int)Math.round(r.getWidth()), (int)Math.round(r.getHeight()));
    }
    
    public Rectangle2D convertPDF2ImageCoord(Rectangle2D r) {
    	IPDFPage page = renderedPageCache.containsKey(currentPage.getPageNumber()) ? renderedPageCache.get(currentPage.getPageNumber()) : currentPage;
    	return convertPDF2ImageCoord(r, page);
    }
    
    public Rectangle2D convertPDF2ImageCoord(Rectangle2D r, IPDFPage page) {
    	if (continuousMode) {
    		Rectangle2D imgRect = page.pdf2ImageCoordinates(r);
    		if (pageOffsets != null && pageOffsets.length >= page.getPageNumber()) {
    			int py = pageOffsets[page.getPageNumber() - 1];
    			imgRect.setRect(imgRect.getX(), imgRect.getY() + py, imgRect.getWidth(), imgRect.getHeight());
    		}
    		return imgRect;
    	}
    	return page.pdf2ImageCoordinates(r);
    }
    
    public Rectangle2D convertImage2PDFCoord(Rectangle2D r) {
    	if (continuousMode) {
    		int y = (int) r.getY();
    		int pageNr = getPageNumberForY(y);
    		IPDFPage page = renderedPageCache.containsKey(pageNr) ? renderedPageCache.get(pageNr) : editor.getPDFFile().getPage(pageNr);
    		int py = (pageOffsets != null && pageOffsets.length >= pageNr) ? pageOffsets[pageNr - 1] : 0;
    		Rectangle2D localRect = new Rectangle2D.Double(r.getX(), r.getY() - py, r.getWidth(), r.getHeight());
    		return page.image2PdfCoordinates(localRect);
    	}
    	IPDFPage page = renderedPageCache.containsKey(currentPage.getPageNumber()) ? renderedPageCache.get(currentPage.getPageNumber()) : currentPage;
    	return page.image2PdfCoordinates(r);
    }
    
    /**
     * Sets the zoom factor and rerenders the current page.
     * @param factor 0 < factor < \infty
     */
    public void setZoomFactor(float factor) {
    	assert (factor > 0);
    	zoomFactor = factor;
    	showPage(currentPage);
    }
    
    /**
     * Returns the current used zoom factor
     * @return
     */
    public float getZoomFactor() {
    	return zoomFactor;
    }
    
    /**
     * Draw the image.
     */
    public void paintControl(PaintEvent event) {
    	GC g = event.gc;
        Point sz = getSize();
        
        org.eclipse.swt.graphics.Region originalClipping = new org.eclipse.swt.graphics.Region(display);
        g.getClipping(originalClipping);
        
        org.eclipse.swt.graphics.Region bgRegion = new org.eclipse.swt.graphics.Region(display);
        g.getClipping(bgRegion);
        
        if (continuousMode) {
        	IPDFFile f = editor.getPDFFile();
        	if (f == null || pageOffsets == null || pageOffsets.length == 0) {
                g.setForeground(display.getSystemColor(SWT.COLOR_BLACK));
                g.drawString(Messages.PDFPageViewer_1, sz.x / 2 - 30, sz.y / 2);
                bgRegion.dispose();
                originalClipping.dispose();
                return;
        	}
        	
        	int startY = event.y;
        	int endY = event.y + event.height;
        	
        	int startPage = -1;
        	int endPage = -1;
        	int numPages = f.getNumPages();
        	if (pageOffsets != null && numPages > 0) {
        		startPage = getPageNumberForY(startY);
        		endPage = getPageNumberForY(endY);
        		if (endPage < startPage) endPage = startPage;
        	}
        	
        	if (startPage != -1) {
        		pruneImageCache(startPage, endPage);
        		
        		for (int pageNr = startPage; pageNr <= endPage; pageNr++) {
        			int i = pageNr - 1;
        			int py = pageOffsets[i];
        			int ph = pageHeights[i];
        			
        			org.eclipse.swt.graphics.Image swtImg = getPageImage(pageNr);
        			if (swtImg != null) {
        				Rectangle rect = swtImg.getBounds();
        				
        				int idealH = pageHeights[i];
        				int idealW = Math.round(zoomFactor * f.getPageWidth(pageNr));
        				
        				int pox = 0;
        				if (centerPage) {
        					pox = (sz.x - idealW) / 2;
        				}
        				
        				Rectangle pageBounds = new Rectangle(pox, py, idealW, idealH);
        				Rectangle clipRect = new Rectangle(event.x, event.y, event.width, event.height);
        				Rectangle intersection = clipRect.intersection(pageBounds);
        				
        				if (intersection.width > 0 && intersection.height > 0) {
        					double scaleX = (double) rect.width / idealW;
        					double scaleY = (double) rect.height / idealH;
        					
        					int srcX = (int) Math.round((intersection.x - pox) * scaleX);
        					int srcY = (int) Math.round((intersection.y - py) * scaleY);
        					int srcW = (int) Math.round(intersection.width * scaleX);
        					int srcH = (int) Math.round(intersection.height * scaleY);
        					
        					if (srcX < 0) { srcW += srcX; srcX = 0; }
        					if (srcY < 0) { srcH += srcY; srcY = 0; }
        					if (srcX + srcW > rect.width) srcW = rect.width - srcX;
        					if (srcY + srcH > rect.height) srcH = rect.height - srcY;
        					
        					if (srcW > 0 && srcH > 0) {
        						try {
        							Float imgZoom = pageImageZoomCache.get(pageNr);
        							if (imgZoom != null && imgZoom == zoomFactor) {
        								g.setInterpolation(SWT.NONE);
        							} else {
        								g.setInterpolation(SWT.HIGH);
        							}
        						} catch (org.eclipse.swt.SWTException e) {
        							// Advanced graphics not available; fall back to default scaling.
        						}
        						g.drawImage(swtImg, srcX, srcY, srcW, srcH, 
        								intersection.x, intersection.y, intersection.width, intersection.height);
        						bgRegion.subtract(intersection.x, intersection.y, intersection.width, intersection.height);
        					}
        				}
        			} else {
         				int pox = 0;
         				if (centerPage) {
         					pox = (sz.x - Math.round(zoomFactor * f.getPageWidth(pageNr))) / 2;
         				}
        				g.setForeground(display.getSystemColor(SWT.COLOR_GRAY));
        				g.drawRectangle(pox, py, Math.round(zoomFactor * f.getPageWidth(pageNr)), ph);
        				g.drawString("Loading page " + pageNr + "...", pox + 10, py + 10);
         			}
        		}
        	}
        } else {
            if (currentPage == null) {
                g.setForeground(display.getSystemColor(SWT.COLOR_BLACK));
                g.drawString(Messages.PDFPageViewer_1, sz.x / 2 - 30, sz.y / 2);
                bgRegion.dispose();
                originalClipping.dispose();
                return;
            }
            
            int pageNr = currentPage.getPageNumber();
            org.eclipse.swt.graphics.Image swtImg = getPageImage(pageNr);
            
            if (swtImg != null) {
            	Rectangle rect = swtImg.getBounds();
            	int imwid = rect.width;
            	int imhgt = rect.height;
            	
            	int idealW = Math.round(zoomFactor * currentPage.getWidth());
            	int idealH = Math.round(zoomFactor * currentPage.getHeight());
            	
                if (centerPage) {
                	ScrolledComposite sc = (ScrolledComposite) getParent();
                	Rectangle clientArea = sc.getClientArea();
                	if (idealW <= clientArea.width) {
                		offx = (sz.x - idealW) / 2;
                	} else {
                		offx = 0;
                	}
                	if (idealH <= clientArea.height) {
                		offy = (sz.y - idealH) / 2;
                	} else {
                		offy = 0;
                	}
                } else {
                    offx = 0;
                    offy = 0;
                }
                
                Rectangle imgBounds = new Rectangle(offx, offy, idealW, idealH);
                Rectangle clipRect = new Rectangle(event.x, event.y, event.width, event.height);
                Rectangle intersection = clipRect.intersection(imgBounds);
 
                if (intersection.width > 0 && intersection.height > 0) {
                	double scaleX = (double) imwid / idealW;
                	double scaleY = (double) imhgt / idealH;
                	
                	int srcX = (int) Math.round((intersection.x - offx) * scaleX);
                	int srcY = (int) Math.round((intersection.y - offy) * scaleY);
                	int srcW = (int) Math.round(intersection.width * scaleX);
                	int srcH = (int) Math.round(intersection.height * scaleY);
                	
                	if (srcX < 0) { srcW += srcX; srcX = 0; }
                	if (srcY < 0) { srcH += srcY; srcY = 0; }
                	if (srcX + srcW > imwid) srcW = imwid - srcX;
                	if (srcY + srcH > imhgt) srcH = imhgt - srcY;
                	
                	if (srcW > 0 && srcH > 0) {
        				try {
        					Float imgZoom = pageImageZoomCache.get(pageNr);
        					if (imgZoom != null && imgZoom == zoomFactor) {
        						g.setInterpolation(SWT.NONE);
        					} else {
        						g.setInterpolation(SWT.HIGH);
        					}
        				} catch (org.eclipse.swt.SWTException e) {
        					// Advanced graphics not available; fall back to default scaling.
        				}
                		g.drawImage(swtImg, srcX, srcY, srcW, srcH, 
                				intersection.x, intersection.y, intersection.width, intersection.height);
                		bgRegion.subtract(intersection.x, intersection.y, intersection.width, intersection.height);
                	}
                }
                
                if (highlightLinks) {
                	IPDFPage page = renderedPageCache.containsKey(currentPage.getPageNumber()) ? renderedPageCache.get(currentPage.getPageNumber()) : currentPage;
                    IPDFLinkAnnotation[] anno = page.getAnnotations();
                    g.setForeground(display.getSystemColor(SWT.COLOR_RED));
                    for (IPDFLinkAnnotation a : anno) {
                        Rectangle r = getRectangle(convertPDF2ImageCoord(a.getPosition(), page));
                        r.x += offx;
                        r.y += offy;
                        g.drawRectangle(r);
                    }
                }
                if (highlight != null) {
                    g.setForeground(display.getSystemColor(SWT.COLOR_BLUE));
                    Rectangle r = getRectangle(highlight);
                    r.x += offx;
                    r.y += offy;
                    g.drawRectangle(r);
                }
            } else {
            	g.setForeground(display.getSystemColor(SWT.COLOR_GRAY));
            	g.drawString("Loading page " + pageNr + "...", sz.x / 2 - 50, sz.y / 2);
            }
        }
        
        g.setClipping(bgRegion);
        g.setBackground(getBackground());
        g.fillRectangle(event.x, event.y, event.width, event.height);
        g.setClipping(originalClipping);
        originalClipping.dispose();
        bgRegion.dispose();
    }

    @Override
    public void preferenceChange(PreferenceChangeEvent event) {
    	if (ToggleLinkHighlightHandler.PREF_LINKHIGHTLIGHT_ID.equals(event.getKey())) {
    		highlightLinks = Boolean.parseBoolean((String)(event.getNewValue()));
    		redraw();
    	} else if (PreferenceConstants.PREF_CENTER_PAGE.equals(event.getKey())) {
    		centerPage = Boolean.parseBoolean((String)(event.getNewValue()));
    		redraw();
    	} else if (PreferenceConstants.PREF_CONTINUOUS_MODE.equals(event.getKey())) {
    		continuousMode = Boolean.parseBoolean((String)(event.getNewValue()));
    		Display.getDefault().asyncExec(new Runnable() {
    			public void run() {
    				if (!isDisposed() && getPage() != null) {
    					if (continuousMode) {
    						lastZoomFactor = zoomFactor;
    						calculateLayout();
    						updateCanvasSize(maxWidth, totalHeight);
    					} else {
    						int newW = Math.round(zoomFactor * getPage().getWidth());
    						int newH = Math.round(zoomFactor * getPage().getHeight());
    						updateCanvasSize(newW, newH);
    					}
    					redraw();
    				}
    			}
    		});
    	}
    }
    
	public int[] getPageOffsets() { return pageOffsets; }
	public int[] getPageHeights() { return pageHeights; }
	public boolean isContinuousMode() { return continuousMode; }

    /**
     * Finds the corresponding page number (1-based) for a given Y coordinate in continuous mode.
     * Uses binary search for O(log N) lookup.
     */
    private int getPageNumberForY(int y) {
        if (pageOffsets == null || pageHeights == null) return 1;
        int low = 0;
        int high = pageOffsets.length - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int py = pageOffsets[mid];
            int ph = pageHeights[mid];
            if (y >= py && y <= py + ph) {
                return mid + 1;
            } else if (y < py) {
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }
        // If y is in the spacing between pages, low will point to the next page.
        // Clamp to valid page range.
        return Math.max(1, Math.min(low + 1, pageOffsets.length));
    }

    /**
     * Gets the page currently being displayed
     */
    public IPDFPage getPage() {
        return currentPage;
    }

    @Override
    public void dispose() {
    	super.dispose();

 		if (currentImage != null) {
 			currentImage.flush();
 			currentImage = null;
 		}
 		clearImageCache();
 		
 		Composite parent = getParent();
 		if (parent != null && !parent.isDisposed() && parentResizeListener != null) {
 			parent.removeListener(SWT.Resize, parentResizeListener);
 		}
     	
     	IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(Activator.PLUGIN_ID);
     	prefs.removePreferenceChangeListener(this);
    }

	public void calculateLayout() {
		IPDFFile f = editor.getPDFFile();
		if (f == null) return;
		int numPages = f.getNumPages();
		pageHeights = new int[numPages];
		pageOffsets = new int[numPages];
		totalHeight = 0;
		maxWidth = 0;
		int spacing = Math.round(zoomFactor * 10);
		for (int i = 0; i < numPages; i++) {
			int h = Math.round(zoomFactor * f.getPageHeight(i + 1));
			int w = Math.round(zoomFactor * f.getPageWidth(i + 1));
			pageHeights[i] = h;
			pageOffsets[i] = totalHeight;
			totalHeight += h + spacing;
			if (w > maxWidth) maxWidth = w;
		}
		if (numPages > 0) {
			totalHeight -= spacing;
		}
	}
	
	public void updateLayout() {
		calculateLayout();
		updateCanvasSize(maxWidth, totalHeight);
	}
	
	public void updateCanvasSize(int idealW, int idealH) {
		Composite parent = getParent();
		if (parent instanceof ScrolledComposite) {
			Rectangle r = ((ScrolledComposite) parent).getClientArea();
			int w = Math.max(r.width, idealW);
			int h = Math.max(r.height, idealH);
			Point cur = getSize();
			if (cur.x != w || cur.y != h) {
				setSize(w, h);
			}
		} else {
			Point cur = getSize();
			if (cur.x != idealW || cur.y != idealH) {
				setSize(idealW, idealH);
			}
		}
	}
	
	public void clearImageCache() {
		for (org.eclipse.swt.graphics.Image img : pageImageCache.values()) {
			if (img != null && !img.isDisposed()) {
				img.dispose();
			}
		}
		pageImageCache.clear();
		pageImageZoomCache.clear();
		renderedPageCache.clear();
		renderingPages.clear();
		retryCounts.clear();
	}

	private void pruneImageCache(int startPage, int endPage) {
		java.util.List<Integer> keysToRemove = new java.util.ArrayList<>();
		for (int key : pageImageCache.keySet()) {
			if (key < startPage - 1 || key > endPage + 1) {
				keysToRemove.add(key);
			}
		}
		for (int key : keysToRemove) {
			org.eclipse.swt.graphics.Image img = pageImageCache.remove(key);
			pageImageZoomCache.remove(key);
			renderedPageCache.remove(key);
			if (img != null && !img.isDisposed()) {
				img.dispose();
			}
		}
	}

	private java.util.Set<Integer> renderingPages = new java.util.HashSet<>();

	private org.eclipse.swt.graphics.Image getPageImage(final int pageNr) {
		org.eclipse.swt.graphics.Image img = pageImageCache.get(pageNr);
		Float imgZoom = pageImageZoomCache.get(pageNr);

		boolean needsRender = false;
		if (img == null || img.isDisposed()) {
			needsRender = true;
		} else if (imgZoom == null || imgZoom != zoomFactor) {
			needsRender = true;
		}

		if (!needsRender) {
			return img;
		}
		
		if (renderingPages.contains(pageNr)) {
			// Return old image as preview while rendering
			return (img != null && !img.isDisposed()) ? img : null;
		}

		final IPDFFile f = editor.getPDFFile();
		if (f == null) {
			// No file (yet); keep showing the old image if we have one.
			return (img != null && !img.isDisposed()) ? img : null;
		}
		renderingPages.add(pageNr);

		int calculatedW = Math.round(zoomFactor * f.getPageWidth(pageNr));
		int calculatedH = Math.round(zoomFactor * f.getPageHeight(pageNr));
		int retryCount = retryCounts.containsKey(pageNr) ? retryCounts.get(pageNr) : 0;
		if (retryCount > 0) {
			// Apply a small size jitter to bypass JPedal rendering bugs at specific resolutions.
			// Alter size by +1, -2, +3 pixels based on retry attempt.
			int offset = (retryCount % 2 == 1) ? retryCount : -retryCount;
			calculatedW = Math.max(1, calculatedW + offset);
			calculatedH = Math.max(1, calculatedH + offset);
		}
		final int w = calculatedW;
		final int h = calculatedH;
		final IPDFFile jobFile = f;
		final float jobZoom = zoomFactor;
		
		org.eclipse.core.runtime.jobs.Job renderJob = new org.eclipse.core.runtime.jobs.Job("Render Page " + pageNr) {
			@Override
			protected org.eclipse.core.runtime.IStatus run(org.eclipse.core.runtime.IProgressMonitor monitor) {
				if (editor.getPDFFile() != jobFile) {
					return org.eclipse.core.runtime.Status.OK_STATUS;
				}
				try {
					Image awtImg;
					final IPDFPage[] renderedPage = new IPDFPage[1];
					synchronized (jobFile) {
						if (editor.getPDFFile() != jobFile) {
							return org.eclipse.core.runtime.Status.OK_STATUS;
						}
						renderedPage[0] = jobFile.getPage(pageNr);
						awtImg = renderedPage[0].getImage(h, w);
					}
					final ImageData imgData = convertToSWT((BufferedImage) awtImg);
					awtImg.flush();
					
					Display.getDefault().asyncExec(new Runnable() {
						@Override
						public void run() {
							if (isDisposed()) {
								return;
							}
							if (editor.getPDFFile() != jobFile) {
								return;
							}
							org.eclipse.swt.graphics.Image swtImg = new org.eclipse.swt.graphics.Image(display, imgData);
							org.eclipse.swt.graphics.Image oldImg = pageImageCache.put(pageNr, swtImg);
							pageImageZoomCache.put(pageNr, jobZoom);
							renderedPageCache.put(pageNr, renderedPage[0]);
							if (oldImg != null && !oldImg.isDisposed()) {
								oldImg.dispose();
							}
							renderingPages.remove(pageNr);
							retryCounts.remove(pageNr);

							if (zoomFactor != jobZoom) {
								// Zoom changed while this render was in flight.
								// Keep the result as an up-to-date preview and trigger
								// a full repaint, which schedules a fresh render at
								// the current zoom factor (renderingPages is now clear).
								redraw();
								return;
							}

							if (continuousMode && pageOffsets != null && pageOffsets.length >= pageNr) {
								int py = pageOffsets[pageNr - 1];
								int ph = pageHeights[pageNr - 1];
								redraw(0, py, getSize().x, ph, false);
							} else {
								redraw();
							}
						}
					});
				} catch (final Exception e) {
					Display.getDefault().asyncExec(new Runnable() {
						@Override
						public void run() {
							if (isDisposed()) {
								return;
							}
							if (editor.getPDFFile() != jobFile) {
								return;
							}
							renderingPages.remove(pageNr);
							Activator.log("Failed to render page " + pageNr, e);
							
							int count = retryCounts.containsKey(pageNr) ? retryCounts.get(pageNr) : 0;
							if (count < 3) {
								retryCounts.put(pageNr, count + 1);
								Display.getDefault().timerExec(1000, new Runnable() {
									@Override
									public void run() {
										if (!isDisposed()) {
											redraw();
										}
									}
								});
							}
						}
					});
				}
				return org.eclipse.core.runtime.Status.OK_STATUS;
			}
		};
		renderJob.setSystem(true);
		renderJob.schedule();
		return (img != null && !img.isDisposed()) ? img : null;
	}
}
