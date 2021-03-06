package org.osehra.integration.core.service;

/**
 * A no operation service. Returns the input as received.
 * 
 * @author Julian Jewel
 */
public class NoOpService implements ServiceInvoker<Object, Object> {

	/**
	 * Invoke a no-operation service. Returns the input as received.
	 * 
	 * @param object
	 *            the input message
	 * @return the input message
	 * @throws ServiceInvocationException
	 *             if an error occured
	 */
	@Override
	public Object invoke(final Object object)
			throws ServiceInvocationException {
		return object;
	}
}
