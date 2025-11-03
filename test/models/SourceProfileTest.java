package models;

import junit.framework.TestCase;

public class SourceProfileTest extends TestCase {

    SourceProfile testprofile = new SourceProfile(
            "techCrunch",
            "https://techcrunch.com",
            "News Outlet focused on Technology."
    );

    public void testGetDescription() {
        assertEquals("News Outlet focused on Technology.", testprofile.getDescription());
    }

    public void testGetUrl() {
        assertEquals("https://techcrunch.com", testprofile.getUrl());
    }

    public void testGetSourceName() {
        assertEquals("techCrunch", testprofile.getSourceName());
    }
}