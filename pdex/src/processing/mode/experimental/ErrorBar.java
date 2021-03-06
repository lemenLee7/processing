/*
  Part of the XQMode project - https://github.com/Manindra29/XQMode

  Under Google Summer of Code 2012 - 
  http://www.google-melange.com/gsoc/homepage/google/gsoc2012

  Copyright (C) 2012 Manindra Moharana

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License version 2
  as published by the Free Software Foundation.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software Foundation,
  Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package processing.mode.experimental;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;

import javax.swing.JPanel;
import javax.swing.SwingWorker;
import javax.swing.text.BadLocationException;

import processing.app.Base;
import processing.app.SketchCode;

/**
 * The bar on the left of the text area which displays all errors as rectangles. <br>
 * <br>
 * All errors and warnings of a sketch are drawn on the bar, clicking on one,
 * scrolls to the tab and location. Error messages displayed on hover. Markers
 * are not in sync with the error line. Similar to eclipse's right error bar
 * which displays the overall errors in a document
 * 
 * @author Manindra Moharana &lt;me@mkmoharana.com&gt;
 * 
 */
public class ErrorBar extends JPanel {
	/**
	 * Preferred height of the component
	 */
	protected int preferredHeight;

	/**
	 * Preferred height of the component
	 */
	protected int preferredWidth = 12;

	/**
	 * Height of marker
	 */
	public static final int errorMarkerHeight = 4;

	/**
	 * Color of Error Marker
	 */
	public Color errorColor = new Color(0xED2630);

	/**
	 * Color of Warning Marker
	 */
	public Color warningColor = new Color(0xFFC30E);

	/**
	 * Background color of the component
	 */
	public Color backgroundColor = new Color(0x2C343D);

	/**
	 * DebugEditor instance
	 */
	protected DebugEditor editor;

	/**
	 * ErrorCheckerService instance
	 */
	protected ErrorCheckerService errorCheckerService;

	/**
	 * Stores error markers displayed PER TAB along the error bar.
	 */
	protected ArrayList<ErrorMarker> errorPoints = new ArrayList<ErrorMarker>();

	/**
	 * Stores previous list of error markers.
	 */
	protected ArrayList<ErrorMarker> errorPointsOld = new ArrayList<ErrorMarker>();

	public void paintComponent(Graphics g) {
		Graphics2D g2d = (Graphics2D) g;
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(backgroundColor);
		g.fillRect(0, 0, getWidth(), getHeight());

		for (ErrorMarker emarker : errorPoints) {
			if (emarker.getType() == ErrorMarker.Error) {
				g.setColor(errorColor);
			} else {
				g.setColor(warningColor);
			}
			g.fillRect(2, emarker.getY(), (getWidth() - 3), errorMarkerHeight);
		}
	}

	public Dimension getPreferredSize() {
		return new Dimension(preferredWidth, preferredHeight);
	}

	public Dimension getMinimumSize() {
		return getPreferredSize();
	}

	public ErrorBar(DebugEditor editor, int height, ExperimentalMode mode) {
		this.editor = editor;
		this.preferredHeight = height;
		this.errorCheckerService = editor.errorCheckerService;
		errorColor = mode.getThemeColor("errorbar.errorcolor", errorColor);
		warningColor = mode
				.getThemeColor("errorbar.warningcolor", warningColor);
		backgroundColor = mode.getThemeColor("errorbar.backgroundcolor",
				backgroundColor);
		addListeners();
	}

	/**
	 * Update error markers in the error bar.
	 * 
	 * @param problems
	 *            - List of problems.
	 */
	synchronized public void updateErrorPoints(final ArrayList<Problem> problems) {

		// NOTE TO SELF: ErrorMarkers are calculated for the present tab only
		// Error Marker index in the arraylist is LOCALIZED for current tab.
		// Also, need to do the update in the UI thread via SwingWorker to prevent
	  // concurrency issues. 
		final int fheight = this.getHeight();
		SwingWorker<Object, Object> worker = new SwingWorker<Object, Object>() {

      protected Object doInBackground() throws Exception {
        SketchCode sc = editor.getSketch().getCurrentCode();
        int totalLines = 0, currentTab = editor.getSketch()
            .getCurrentCodeIndex();
        try {
          totalLines = Base.countLines(sc.getDocument()
              .getText(0, sc.getDocument().getLength())) + 1;
        } catch (BadLocationException e) {
          e.printStackTrace();
        }
        // System.out.println("Total lines: " + totalLines);
        synchronized (errorPoints) {
          errorPointsOld.clear();
          for (ErrorMarker marker : errorPoints) {
            errorPointsOld.add(marker);
          }
          errorPoints.clear();
  
          // Each problem.getSourceLine() will have an extra line added
          // because of
          // class declaration in the beginning as well as default imports
          synchronized (problems) {
            for (Problem problem : problems) {
              if (problem.getTabIndex() == currentTab) {
                // Ratio of error line to total lines
                float y = (problem.getLineNumber() + 1)
                    / ((float) totalLines);
                // Ratio multiplied by height of the error bar
                y *= fheight - 15; // -15 is just a vertical offset
                errorPoints
                    .add(new ErrorMarker(problem, (int) y,
                                         problem.isError() ? ErrorMarker.Error
                                             : ErrorMarker.Warning));
                // System.out.println("Y: " + y);
              }
            }
          }
        }
        return null;
      }

			protected void done() {
				repaint();
			}
		};

		try {
			worker.execute(); // I eat concurrency bugs for breakfast.
		} catch (Exception exp) {
			System.out.println("Errorbar update markers is slacking."
					+ exp.getMessage());
			// e.printStackTrace();
		}
	}

	/**
	 * Check if new errors have popped up in the sketch since the last check
	 * 
	 * @return true - if errors have changed
	 */
	public boolean errorPointsChanged() {
		if (errorPointsOld.size() != errorPoints.size()) {
			editor.getTextArea().repaint();
			// System.out.println("2 Repaint " + System.currentTimeMillis());
			return true;
		}

		else {
			for (int i = 0; i < errorPoints.size(); i++) {
				if (errorPoints.get(i).getY() != errorPointsOld.get(i).getY()) {
					editor.getTextArea().repaint();
					// System.out.println("3 Repaint " +
					// System.currentTimeMillis());
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Add various mouse listeners.
	 */
	protected void addListeners() {

		this.addMouseListener(new MouseAdapter() {

			// Find out which error/warning the user has clicked
			// and then scroll to that
			@Override
			public void mouseClicked(final MouseEvent e) {
        SwingWorker<Object, Object> worker = new SwingWorker<Object, Object>() {

          protected Object doInBackground() throws Exception {
            for (ErrorMarker eMarker : errorPoints) {
              // -2 and +2 are extra allowance, clicks in the
              // vicinity of the markers register that way
              if (e.getY() >= eMarker.getY() - 2
                  && e.getY() <= eMarker.getY() + 2 + errorMarkerHeight) {
                errorCheckerService.scrollToErrorLine(eMarker.getProblem());
                return null;
              }
            }
            return null;
          }
        };

				try {
					worker.execute();
				} catch (Exception exp) {
					System.out.println("Errorbar mouseClicked is slacking."
							+ exp.getMessage());
					// e.printStackTrace();
				}

			}
		});

		// Tooltip on hover
		this.addMouseMotionListener(new MouseMotionListener() {

			@Override
			public void mouseMoved(final MouseEvent evt) {
        // System.out.println(e);
        SwingWorker<Object, Object> worker = new SwingWorker<Object, Object>() {

          protected Object doInBackground() throws Exception {
            for (ErrorMarker eMarker : errorPoints) {
              if (evt.getY() >= eMarker.getY() - 2
                  && evt.getY() <= eMarker.getY() + 2 + errorMarkerHeight) {
                Problem p = eMarker.getProblem();
                String msg = (p.isError() ? "Error: " : "Warning: ")
                    + p.getMessage();
                setToolTipText(msg);
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                break;
              }
            }
            return null;
          }
        };
				
				try {
					worker.execute();
				} catch (Exception exp) {
					System.out
							.println("Errorbar mousemoved Worker is slacking."
									+ exp.getMessage());
					// e.printStackTrace();
				}
			}

			@Override
			public void mouseDragged(MouseEvent arg0) {

			}
		});

	}

}
