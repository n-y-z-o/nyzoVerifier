package co.nyzo.verifier.web.elements;

import co.nyzo.verifier.util.PreferencesUtil;
public class Head extends HtmlTag {

    @Override
    public String getName() {
        return "head";
    }

    public Head addStandardMetadata() {
        add(new Meta().attr("name", "format-detection").attr("content", "telephone=no"));
        add(new Meta().attr("name", "viewport")
                .attr("content", "width=device-width, initial-scale=1, maximum-scale=1"));
        return this;
    }

    public Head addStandardMetadata(String title) {
        return addStandardMetadata(title, title);
    }

    public Head addStandardMetadata(String title, String description) {
        addStandardMetadata();
        add(new Title(title));

        // Get the social image URL, width, and height from preferences. This process could be made more dynamic, if
        // necessary.
        String imageUrl = PreferencesUtil.get("social_image_url", null);
        int imageWidth = PreferencesUtil.getInt("social_image_width", -1);
        int imageHeight = PreferencesUtil.getInt("social_image_height", -1);

        // Add Facebook Open Graph metadata.
        add(new Meta().attr("property", "og:title").attr("content", title));
        add(new Meta().attr("property", "og:description").attr("content", description));
        add(new Meta().attr("property", "og:type").attr("content", "website"));
        if (imageUrl != null && imageWidth > 0 && imageHeight > 0) {
            add(new Meta().attr("property", "og:image").attr("content", imageUrl));
            add(new Meta().attr("property", "og:image:width").attr("content", imageWidth + ""));
            add(new Meta().attr("property", "og:image:height").attr("content", imageHeight + ""));
        }

        // Add Twitter Card metadata.
        add(new Meta().attr("name", "twitter:card").attr("content", "summary_large_image"));
        add(new Meta().attr("name", "twitter:site").attr("content", PreferencesUtil.get("twitter_site", "@Nyzo16")));
        add(new Meta().attr("name", "twitter:creator").attr("content", PreferencesUtil.get("twitter_creator",
                "@Nyzo16")));
        add(new Meta().attr("name", "twitter:title").attr("content", title));
        if (!description.equals(title)) {
            add(new Meta().attr("name", "twitter:description").attr("content", description));
        }
        if (imageUrl != null) {
            add(new Meta().attr("name", "twitter:image").attr("content", imageUrl));
        }

        return this;
    }
}
