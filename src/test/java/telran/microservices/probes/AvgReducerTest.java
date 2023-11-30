package telran.microservices.probes;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;

import telran.microservices.probes.dto.Probe;
import telran.microservices.probes.entities.ListProbesValues;
import telran.microservices.probes.repo.ListProbeRepo;

@SpringBootTest
@Import(TestChannelBinderConfiguration.class) // имитатор кафки
public class AvgReducerTest {
	private static final long PROBE_ID_NO_AVG = 123;
	private static final long PROBE_ID_AVG = 124;
	private static final long PROBE_ID_NO_VALUES = 125;
	private static final int VALUE = 100;
	@Autowired
	InputDestination producer;
	@Autowired
	OutputDestination consumer;
	@MockBean // аннотация которая показывает что будем использовать фейковый репозиторий
				// Redux
	ListProbeRepo listProbeRepo;
	static List<Integer> valuesNoAvg;
	static List<Integer> valuesAvg;
	static ListProbesValues listProbeNoAvg = new ListProbesValues(PROBE_ID_NO_AVG);
	static ListProbesValues listProbeAvg = new ListProbesValues(PROBE_ID_AVG);
	static HashMap<Long, ListProbesValues> redisMap = new HashMap<>();
	Probe probeNoValues = new Probe(PROBE_ID_NO_VALUES, VALUE);
	Probe probeNoAvg = new Probe(PROBE_ID_NO_AVG, VALUE);
	Probe probeAvg = new Probe(PROBE_ID_AVG, VALUE);
	private String producerBindingName = "avg-Producer-out-0";
	private String consumerBindingNmae = "avgConsumer-in-0";

	@BeforeAll
	static void setUpAll() {
		valuesNoAvg = listProbeNoAvg.getValues();
		valuesAvg = listProbeAvg.getValues();
		valuesAvg.add(VALUE);
		redisMap.put(PROBE_ID_AVG, listProbeAvg);
		redisMap.put(PROBE_ID_NO_AVG, listProbeNoAvg);
	}
	
	@Test
	void probeNoValuesTest() {
		when(listProbeRepo.findById(PROBE_ID_NO_VALUES))
		.thenReturn(Optional.ofNullable(null));
		when(listProbeRepo.save(new ListProbesValues(PROBE_ID_NO_VALUES)))
		.thenAnswer(new Answer<ListProbesValues>() {
			@Override
			public ListProbesValues answer (InvocationOnMock invocation) throws Throwable{
				redisMap.put(PROBE_ID_NO_VALUES, invocation.getArgument(0));
				return invocation.getArgument(0);
			}
		});
		producer.send(new GenericMessage<Probe>(probeNoValues), consumerBindingNmae );
		Message<byte[]> message = consumer.receive(100, producerBindingName);
		assertNull(message);
		assertEquals(VALUE, redisMap.get(PROBE_ID_NO_VALUES).getValues().get(0));
	}
	
	@Test
	void probeNoAvgTest() {
		when(listProbeRepo.findById(PROBE_ID_NO_AVG))
		.thenReturn(Optional.ofNullable(listProbeNoAvg));
		when(listProbeRepo.save(new ListProbesValues(PROBE_ID_NO_AVG)))
		.thenAnswer(new Answer<ListProbesValues>() {
			@Override
			public ListProbesValues answer (InvocationOnMock invocation) throws Throwable{
				redisMap.put(PROBE_ID_NO_AVG, invocation.getArgument(0));
				return invocation.getArgument(0);
			}
		});
		producer.send(new GenericMessage<Probe>(probeNoValues), consumerBindingNmae );
		Message<byte[]> message = consumer.receive(100, producerBindingName);
		assertNull(message);
		assertEquals(VALUE, redisMap.get(PROBE_ID_NO_AVG).getValues().get(0));
	}
	
	@Test
	void probeAvgTest() throws StreamReadException, DatabindException, IOException {
		when(listProbeRepo.findById(PROBE_ID_AVG))
		.thenReturn(Optional.ofNullable(listProbeNoAvg));
		when(listProbeRepo.save(new ListProbesValues(PROBE_ID_AVG)))
		.thenAnswer(new Answer<ListProbesValues>() {
			@Override
			public ListProbesValues answer (InvocationOnMock invocation) throws Throwable{
				redisMap.put(PROBE_ID_AVG, invocation.getArgument(0));
				return invocation.getArgument(0);
			}
		});
		producer.send(new GenericMessage<Probe>(probeNoValues), consumerBindingNmae );
		Message<byte[]> message = consumer.receive(100, producerBindingName);
		assertNotNull(message);
		ObjectMapper mapper = new ObjectMapper();
		assertEquals(probeAvg, mapper.readValue(message.getPayload(), Probe.class));
		assertEquals(VALUE, redisMap.get(PROBE_ID_NO_AVG).getValues().get(0));
	}

}
