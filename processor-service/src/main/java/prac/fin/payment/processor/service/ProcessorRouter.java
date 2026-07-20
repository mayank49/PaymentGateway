package prac.fin.payment.processor.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import prac.fin.payment.common.exception.ProcessorException;

/**
 * Selects the correct PaymentProcessor implementation at runtime.
 */
@Slf4j
@Component
public class ProcessorRouter {

	private final Map<String, PaymentProcessor> processors;
	
	/**
     * Spring injects all PaymentProcessor beans here automatically.
     * If you add StripeProcessorAdapter, it appears in this list.
     */
    public ProcessorRouter(List<PaymentProcessor> processorList) {
        this.processors = processorList.stream()
                .collect(Collectors.toMap(
                        PaymentProcessor::getName,
                        Function.identity()
                ));
 
        log.info("Registered payment processors: {}", processors.keySet());
    }
    
    /**
     * Returns the processor with the given name.
     * The name comes from application.yml or merchant config.
     *
     * Future routing logic can be added here:
     *   - Check if processor is healthy (circuit breaker)
     *   - Fall back to secondary if primary is down
     *   - Route UPI to a UPI-specific processor
     */
    public PaymentProcessor route(String processorName) {
        var processor = processors.get(processorName);
 
        if (processor == null) {
            throw new ProcessorException(
                    processorName,
                    "No processor registered with name: " + processorName
                    + ". Available: " + processors.keySet()
            );
        }
 
        return processor;
    }
 
    public boolean isAvailable(String processorName) {
        return processors.containsKey(processorName);
    }
}
