package co.nyzo.verifier.web;

public enum HttpStatusCode {

    // This enumeration provides only a small subset of status codes that the WebListener currently uses.

    Ok200(200, "OK"),
    PaymentRequired402(402, "Payment Required"),
    NotFound404(404, "Not Found");

    private int code;
    private String label;

    HttpStatusCode(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }
}
