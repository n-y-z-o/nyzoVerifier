package co.nyzo.verifier.web;

public enum HttpMethod {

    Get,
    Post;

    public static HttpMethod forString(String value) {

        // Default to GET. Convert the input to uppercase for a case-insensitive lookup.
        value = value.toUpperCase();
        HttpMethod result = HttpMethod.Get;
        for (HttpMethod method : values()) {
            if (method.toString().equals(value)) {
                result = method;
            }
        }

        return result;
    }

    @Override
    public String toString() {
        return super.toString().toUpperCase();
    }
}
