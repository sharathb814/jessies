package e.ptextarea;


import java.awt.*;
import java.util.*;
import java.util.regex.*;

import java.util.List;

/**
 * A PCLikeTextStyler does the main work for the C, C++ and Java stylers.  The C, C++ and
 * Java subclasses provide only information about valid keywords.  This class understands the
 * single- and multi-line comment structures, quoted strings, and how to find keywords in what's
 * left over.
 * 
 * @author Phil Norman
 */

public abstract class PCLikeTextStyler implements PTextStyler, PTextListener {
    private PTextArea textArea;
    private HashSet keywords = new HashSet();
    private int lastGoodLine;
    private boolean[] commentCache;
    private Pattern keywordPattern = Pattern.compile("\\b\\w+\\b");
    
    private static final int TYPE_NORMAL = 0;
    private static final int TYPE_STRING = 1;
    private static final int TYPE_COMMENT = 2;
    private static final int TYPE_KEYWORD = 3;
    private static final int TYPE_ERROR = 4;
    
    private static final Color[] DEFAULT_COLORS = new Color[5];
    static {
        DEFAULT_COLORS[TYPE_NORMAL] = Color.BLACK;
        DEFAULT_COLORS[TYPE_STRING] = Color.decode("#0000ff");
        DEFAULT_COLORS[TYPE_COMMENT] = Color.decode("#227722");
        DEFAULT_COLORS[TYPE_KEYWORD] = Color.decode("#770022");
        DEFAULT_COLORS[TYPE_ERROR] = Color.decode("#ff0000");
    }
    
    public PCLikeTextStyler(PTextArea textArea) {
        this.textArea = textArea;
        initCommentCache();
        textArea.getPTextBuffer().addTextListener(this);
        textArea.setTextStyler(this);
    }
    
    protected void addKeywords(String[] keywordList) {
        for (int i = 0; i < keywordList.length; i++) {
            keywords.add(keywordList[i]);
        }
    }
    
    /**
     * Returns true if the styler should comment to end of line on seeing '#'.
     */
    public abstract boolean supportShellComments();
    
    public Color getDefaultColor(int index) {
        return DEFAULT_COLORS[index];
    }
    
    private void initCommentCache() {
        lastGoodLine = 0;
        commentCache = new boolean[100];
    }
    
    private void ensureCommentCacheLength(int index) {
        if (index >= commentCache.length) {
            boolean[] newCache = new boolean[index + 100];
            System.arraycopy(commentCache, 0, newCache, 0, commentCache.length);
            commentCache = newCache;
        }
    }

    public PTextSegment[] getLineSegments(PTextArea.SplitLine splitLine) {
        int lineIndex = splitLine.getLineIndex();
        String fullLine = textArea.getLineList().getLine(lineIndex).getContents().toString();
        List segments = getLineSegments(lineIndex, fullLine);
        int index = 0;
        ArrayList result = new ArrayList();
        int start = splitLine.getOffset();
        int end = start + splitLine.getLength();
        
        for (int i = 0; index < end && i < segments.size(); ++i) {
            PTextSegment segment = (PTextSegment) segments.get(i);
            if (start >= index + segment.getLength()) {
                index += segment.getLength();
                continue;
            }
            if (start > index) {
                int skip = start - index;
                segment = segment.subSegment(skip);
                index += skip;
            }
            if (end < index + segment.getLength()) {
                segment = segment.subSegment(0, end - index);
            }
            result.add(segment);
            index += segment.getLength();
        }
        return (PTextSegment[]) result.toArray(new PTextSegment[result.size()]);
    }
    
    private List getLineSegments(int lineIndex, String line) {
        List mainSegments = getMainSegments(lineIndex, line);
        if (keywords.size() == 0) {
            return mainSegments;
        } else {
            ArrayList result = new ArrayList();
            for (int i = 0; i < mainSegments.size(); i++) {
                PTextSegment mainSegment = (PTextSegment) mainSegments.get(i);
                if (mainSegment.getStyleIndex() == TYPE_NORMAL) {
                    result.addAll(getKeywordAddedSegments(mainSegment));
                } else {
                    result.add(mainSegment);
                }
            }
            return result;
        }
    }
    
    private List getKeywordAddedSegments(PTextSegment segment) {
        ArrayList result = new ArrayList();
        String text = segment.getText();
        Matcher matcher = keywordPattern.matcher(text);
        int normalStart = 0;
        while (matcher.find()) {
            String keyword = matcher.group();
            if (keywords.contains(keyword)) {
                result.add(new PTextSegment(TYPE_NORMAL, text.substring(normalStart, matcher.start())));
                result.add(new PTextSegment(TYPE_KEYWORD, keyword));
                normalStart = matcher.end();
            }
        }
        if (segment.getText().length() > normalStart) {
            result.add(new PTextSegment(TYPE_NORMAL, text.substring(normalStart)));
        }
        return result;
    }
    
    private List getMainSegments(int lineIndex, String line) {
        boolean comment = startsCommented(lineIndex);
        ArrayList result = new ArrayList();
        int lastStart = 0;
        for (int i = 0; i < line.length(); ) {
            if (comment) {
                int commentEndIndex = line.indexOf("*/", i);
                if (commentEndIndex == -1) {
                    commentEndIndex = line.length();
                } else {
                    commentEndIndex += 2;
                }
                result.add(new PTextSegment(TYPE_COMMENT, line.substring(lastStart, commentEndIndex)));
                i = commentEndIndex;
                lastStart = commentEndIndex;
                comment = false;
            } else {
                char ch = line.charAt(i);
                if (supportShellComments() && ch == '#') {
                    comment = true;
                    if (lastStart < i) {
                        result.add(new PTextSegment(TYPE_NORMAL, line.substring(lastStart, i)));
                    }
                    result.add(new PTextSegment(TYPE_COMMENT, line.substring(i)));
                    i = line.length();
                    lastStart = i;
                } else if (ch == '/') {
                    if (i < line.length() - 1) {
                        if (line.charAt(i + 1) == '*') {
                            comment = true;
                            if (lastStart < i) {
                                result.add(new PTextSegment(TYPE_NORMAL, line.substring(lastStart, i)));
                            }
                            lastStart = i;
                            i += 2;
                        } else if (line.charAt(i + 1) == '/') {
                            if (lastStart < i) {
                                result.add(new PTextSegment(TYPE_NORMAL, line.substring(lastStart, i)));
                            }
                            result.add(new PTextSegment(TYPE_COMMENT, line.substring(i)));
                            i = line.length();
                            lastStart = i;
                        } else {
                            i++;
                        }
                    } else {
                        i++;
                    }
                } else if (ch == '"' || ch == '\'') {
                    if (lastStart < i) {
                        result.add(new PTextSegment(TYPE_NORMAL, line.substring(lastStart, i)));
                    }
                    int stringEnd = i + 1;
                    String matchString = String.valueOf(line.charAt(i));
                    while (stringEnd != -1) {
                        stringEnd = line.indexOf(matchString, stringEnd);
                        if (stringEnd != -1) {
                            stringEnd++;
                            if (getBackslashBeforeCount(line, stringEnd - 1) % 2 == 0) {
                                break;  // Not escaped.
                            }
                        }
                    }
                    // If it falls out because stringEnd == -1, we have an unterminated string.
                    if (stringEnd == -1) {
                        result.add(new PTextSegment(TYPE_ERROR, line.substring(i)));
                        i = line.length();
                    } else {
                        result.add(new PTextSegment(TYPE_STRING, line.substring(i, stringEnd)));
                        i = stringEnd;
                    }
                    lastStart = i;
                } else {
                    i++;
                }
            }
        }
        if (lastStart < line.length()) {
            result.add(new PTextSegment(comment ? TYPE_COMMENT : TYPE_NORMAL, line.substring(lastStart, line.length())));
        }
        return result;
    }
    
    private int getBackslashBeforeCount(String string, int index) {
        int result = 0;
        for (int i = index - 1; i >= 0; i--) {
            if (string.charAt(i) == '\\') {
                result++;
            } else {
                break;
            }
        }
        return result;
    }
    
    private boolean startsCommented(int lineIndex) {
        if (lastGoodLine < lineIndex) {
            ensureCommentCacheLength(lineIndex);
            PLineList lineList = textArea.getLineList();
            for (int i = lastGoodLine; i < lineIndex; i++) {
                String line = lineList.getLine(i).getContents().toString();
                commentCache[i + 1] = lineEndsCommented(line, commentCache[i]);
            }
            lastGoodLine = lineIndex;
        }
        return commentCache[lineIndex];
    }
    
    /**
     * Returns true if the given line will end commented. By "end commented",
     * I think this means "end in an open comment that implies that the next
     * line begins inside a comment".
     */
    private boolean lineEndsCommented(String line, boolean startsCommented) {
        boolean comment = startsCommented;
        int index = 0;
        while (true) {
            if (comment) {
                // Commented - comments eat strings.
                int endIndex = line.indexOf("*/", index);
                if (endIndex == -1) {
                    break;
                }
                comment = false;
                index = endIndex + 2;
            } else {
                // Uncommented - strings eat comments.
                char previous = 0;
                char lastQuote = 0;
                boolean escaped = false;
                for (int i = index; i < line.length(); i++) {
                    char thisChar = line.charAt(i);
                    if (lastQuote == 0) {
                        if (escaped == false && isQuote(thisChar)) {
                            lastQuote = thisChar;
                        }
                        if (previous == '/' && thisChar == '*') {
                            comment = true;
                            index = i + 1;
                            break;
                        } else if (previous == '/' && thisChar == '/') {
                            break;
                        }
                    } else {
                        if (escaped == false && thisChar == lastQuote) {
                            lastQuote = 0;
                        }
                    }
                    if (thisChar == '\\') {
                        escaped = !escaped;
                    } else {
                        escaped = false;
                    }
                    previous = thisChar;
                }
                if (comment == false) {
                    break;
                }
            }
        }
        return comment;
    }
    
    /** Returns true iff the given char is a quote of some sort. */
    public boolean isQuote(char ch) {
        return (ch == '\'' || ch == '\"');
    }

    public void textInserted(PTextEvent event) {
        dirtyFromOffset(event);
    }
    
    public void textRemoved(PTextEvent event) {
        dirtyFromOffset(event);
    }
    
    private void dirtyFromOffset(PTextEvent event) {
        CharSequence seq = textArea.getPTextBuffer();
        StringBuffer buf = new StringBuffer();
        String prefix = seq.subSequence(Math.max(0, event.getOffset() - 2), event.getOffset()).toString();
        int endIndex = event.getOffset();
        if (event.isInsert()) {
            endIndex += event.getLength();
        }
        String suffix = seq.subSequence(endIndex, Math.min(endIndex + 1, seq.length())).toString();
        String withMiddleText = prefix + event.getString() + suffix;
        String withoutMiddleText = prefix + suffix;
        if (hasCommentMarker(withMiddleText) || hasCommentMarker(withoutMiddleText)) {
            lastGoodLine = Math.min(lastGoodLine, textArea.getLineList().getLineIndex(event.getOffset()));
            textArea.repaintFromLine(textArea.getSplitLineIndex(lastGoodLine));
        }
    }
    
    private boolean hasCommentMarker(String text) {
        return (text.indexOf("/*") != -1) || (text.indexOf("*/") != -1);
    }
    
    public void textCompletelyReplaced(PTextEvent event) {
        initCommentCache();
    }
}
