package terminatorn;

import java.awt.*;

/**

@author Phil Norman
*/

public class TheHighlighter implements Highlighter {
	private static final Style STYLE = new Style(null, Color.cyan, null, null);

	public String getName() {
		return "The Highlighter";
	}
	
	/** Request to add highlights to all lines of the view from the index given onwards. */
	public void addHighlights(JTextBuffer view, int firstLineIndex) {
		TextBuffer model = view.getModel();
		for (int i = firstLineIndex; i < model.getLineCount(); i++) {
			String line = model.getLine(i);
			for (int index = line.indexOf("the"); index != -1; index = line.indexOf("the", index + 1)) {
				view.addHighlight(new Highlight(this, new Location(i, index), new Location(i, index + 3), STYLE));
			}
		}
	}
}