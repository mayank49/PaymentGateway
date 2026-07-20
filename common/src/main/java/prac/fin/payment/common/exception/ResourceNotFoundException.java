package prac.fin.payment.common.exception;

public class ResourceNotFoundException extends PaymentGatewayException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 8190656348899219153L;

	public ResourceNotFoundException(String resourceType, String id) {
        super(resourceType + " not found: " + id);
    }
}
