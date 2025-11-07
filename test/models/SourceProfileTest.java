package models;

import junit.framework.TestCase;

/**
 * Unit tests for {@link SourceProfile}.
 * Verifies that getters return the values supplied to the constructor.
 * Uses JUnit 3-style {@link junit.framework.TestCase}.
 * @author Haytham
 */
public class SourceProfileTest extends TestCase {
    /**
     * Ensures {@link SourceProfile#getDescription()} returns
     * the description passed at construction.
     * @author Haytham
     */
    SourceProfile testprofile = new SourceProfile(
            "techCrunch",
            "https://techcrunch.com",
            "News Outlet focused on Technology."
    );
    /**
     * Ensures {@link SourceProfile#getUrl()} returns
     * the URL passed at construction.
     * @author Haytham
     */
    public void testGetDescription() {
        assertEquals("News Outlet focused on Technology.", testprofile.getDescription());
    }

    /**
     * Ensures {@link SourceProfile#getSourceName()} returns
     * the source name passed at construction.
     * @author Haytham
     */
    public void testGetUrl() {
        assertEquals("https://techcrunch.com", testprofile.getUrl());
    }

    /**
     * Unit tests for {@link SourceProfile} using JUnit 4.
     * Verifies constructor → getter behavior for all fields.
     * @author Haytham
     */
    public void testGetSourceName() {
        assertEquals("techCrunch", testprofile.getSourceName());
    }
}