package uk.dsxt.voting.common;

import org.junit.Ignore;
import org.junit.Test;
import uk.dsxt.voting.common.domain.dataModel.*;
import uk.dsxt.voting.common.iso20022.Iso20022Serializer;
import uk.dsxt.voting.common.iso20022.jaxb.MeetingInstruction;
import uk.dsxt.voting.common.iso20022.jaxb.MeetingNotification;
import uk.dsxt.voting.common.messaging.MessagesSerializer;
import uk.dsxt.voting.common.messaging.SimpleSerializer;
import uk.dsxt.voting.common.utils.MessageBuilder;
import uk.dsxt.voting.common.utils.PropertiesHelper;

import javax.xml.bind.*;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

@Ignore
public class XmlTest {
    @Test
    public void testJAXBSerialization() throws Exception {
        //deserialization
        JAXBContext miContext = JAXBContext.newInstance(MeetingInstruction.class);
        Unmarshaller miUnmarshaller = miContext.createUnmarshaller();
        String miXml = PropertiesHelper.getResourceString("mi.xml", "windows-1251");
        StringReader miReader = new StringReader(miXml);
        MeetingInstruction mi = (MeetingInstruction) JAXBIntrospector.getValue(miUnmarshaller.unmarshal(miReader));
        assertNotNull(mi);

        JAXBContext mnContext = JAXBContext.newInstance(MeetingNotification.class);
        Unmarshaller mnUnmarshaller = mnContext.createUnmarshaller();
        String mnXml = PropertiesHelper.getResourceString("mn.xml", "windows-1251");
        Source mnSource = new StreamSource(new StringReader(mnXml));
        JAXBElement<MeetingNotification> mnRoot = mnUnmarshaller.unmarshal(mnSource, MeetingNotification.class);
        MeetingNotification mn = mnRoot.getValue();
        assertNotNull(mn);

        //serialization
        JAXBContext context = JAXBContext.newInstance(MeetingInstruction.class);
        Marshaller m = context.createMarshaller();
        m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        //m.setProperty(Marshaller.JAXB_ENCODING, "windows-1251");
        m.marshal(mi, System.out);

        context = JAXBContext.newInstance(MeetingInstruction.class);
        m = context.createMarshaller();
        m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        //m.setProperty(Marshaller.JAXB_ENCODING, "windows-1251");
        m.marshal(mn, System.out);
    }

    @Test
    public void testSimpleVotingSerialization() throws Exception {
        //deserialization from xml file
        Iso20022Serializer serializer = new Iso20022Serializer();
        String votingXml = PropertiesHelper.getResourceString("voting_simple.xml", "windows-1251");
        Voting voting = serializer.deserializeVoting(votingXml);
        assertNotNull(voting);

        assertEquals("123456", voting.getId());
        assertEquals("GMET_123456", voting.getName());
        assertEquals("GMET",voting.getType());

        assertEquals(4, voting.getQuestions().length);
        assertEquals("1.1", voting.getQuestions()[0].getId());
        assertEquals(3, voting.getQuestions()[0].getAnswers().length);
        assertEquals("1.2", voting.getQuestions()[1].getId());
        assertEquals(3, voting.getQuestions()[1].getAnswers().length);
        assertEquals("2.1", voting.getQuestions()[2].getId());
        assertEquals(3, voting.getQuestions()[2].getAnswers().length);
        assertEquals("3.1", voting.getQuestions()[3].getId());
        assertEquals(3, voting.getQuestions()[3].getAnswers().length);

        //serialization result to string
        String result = serializer.serialize(voting);
        assertNotNull(result);
        System.out.println(result);

        //deserialization from serialized object
        Voting serializedVoting = serializer.deserializeVoting(result);
        assertNotNull(serializedVoting);
        //check that resulted object equals voting deserialized from file
        assertEquals(voting, serializedVoting);
    }

    @Test
    public void testSimpleVoteResultSerialization() throws Exception {
        Iso20022Serializer votingSerializer = new Iso20022Serializer();
        MessagesSerializer simpleSerializer = new SimpleSerializer();
        String votingXml = PropertiesHelper.getResourceString("voting_simple.xml", "windows-1251");
        Voting voting = votingSerializer.deserializeVoting(votingXml);
        assertNotNull(voting);
        
        //deserialization from xml file
        Iso20022Serializer serializer = new Iso20022Serializer();
        String voteResultXml = PropertiesHelper.getResourceString("voteResult_simple.xml", "windows-1251");
        VoteResult voteResult = serializer.deserializeVoteResult(MessageBuilder.buildMessage(voteResultXml, simpleSerializer.serialize(voting)));
        assertNotNull(voteResult);

        assertEquals("МХ1", voteResult.getHolderId());
        assertEquals("000001", voteResult.getVotingId());

        assertEquals(4, voteResult.getAnswers().size());

        //serialization result to string
        String result = serializer.serialize(voteResult, voting);
        assertNotNull(result);
        System.out.println(result);

        //deserialization from serialized object
        VoteResult serializedVoteResult = serializer.deserializeVoteResult(result);
        assertNotNull(serializedVoteResult);
        //check that resulted object equals voting deserialized from file
        assertEquals(voteResult, serializedVoteResult);
    }

    @Test
    public void testCumulativeVotingSerialization() throws Exception {
        //deserialization from xml file
        Iso20022Serializer serializer = new Iso20022Serializer();
        String votingXml = PropertiesHelper.getResourceString("voting_cumulative.xml", "windows-1251");
        Voting voting = serializer.deserializeVoting(votingXml);
        assertNotNull(voting);

        assertEquals("123456", voting.getId());
        assertEquals("Тестовое голосование", voting.getName());
        assertEquals("GMET", voting.getType());

        assertEquals(5, voting.getQuestions().length);
        assertEquals("1.1", voting.getQuestions()[0].getId());
        assertEquals(3, voting.getQuestions()[0].getAnswers().length);
        assertEquals("1.2", voting.getQuestions()[1].getId());
        assertEquals(3, voting.getQuestions()[1].getAnswers().length);
        assertEquals("2.1", voting.getQuestions()[2].getId());
        assertEquals(3, voting.getQuestions()[2].getAnswers().length);
        assertEquals("2.1.multi", voting.getQuestions()[3].getId());
        assertEquals(3, voting.getQuestions()[3].getAnswers().length);
        assertEquals("3.1", voting.getQuestions()[4].getId());
        assertEquals(3, voting.getQuestions()[4].getAnswers().length);

        //serialization result to string
        String result = serializer.serialize(voting);
        assertNotNull(result);
        System.out.println(result);

        //deserialization from serialized object
        Voting serializedVoting = serializer.deserializeVoting(result);
        assertNotNull(serializedVoting);
        //check that resulted object equals voting deserialized from file
        assertEquals(voting, serializedVoting);
    }

    @Test
    public void testCumulativeVoteResultSerialization() throws Exception {
        Iso20022Serializer serializer = new Iso20022Serializer();
        MessagesSerializer simpleSerializer = new SimpleSerializer();
        //deserialize voting
        String votingXml = PropertiesHelper.getResourceString("voting_cumulative.xml", "windows-1251");
        Voting voting = serializer.deserializeVoting(votingXml);
        assertNotNull(voting);

        //deserialization from xml file
        String voteResultXml = PropertiesHelper.getResourceString("voteResult_cumulative.xml", "windows-1251");
        VoteResult voteResult = serializer.deserializeVoteResult(MessageBuilder.buildMessage(voteResultXml, simpleSerializer.serialize(voting)));
        assertNotNull(voteResult);

        assertEquals("МХ1", voteResult.getHolderId());
        assertEquals("000001", voteResult.getVotingId());
        assertEquals(6, voteResult.getAnswers().size());
        //check that deserialization already adapt questions
        assertEquals(4, voteResult.getAnswers().stream().map(VotedAnswer::getQuestionId).distinct().count());

        //serialization result to string
        String result = serializer.serialize(voteResult, voting);
        assertNotNull(result);
        System.out.println(result);

        //deserialization from serialized object
        VoteResult serializedVoteResult = serializer.deserializeVoteResult(result);
        assertNotNull(serializedVoteResult);
        //check that resulted object equals voting deserialized from file
        assertEquals(voteResult, serializedVoteResult);
    }

    @Test
    public void testJsonVotingSerialization() throws Exception {
        final Question[] questions = new Question[1];
        final Answer[] answers = new Answer[1];
        answers[0] = new Answer("1", null);
        questions[0] = new Question("1", "question", answers);
        Voting voting = new Voting("voting_id", "voting_name", "GMET", 0, 0, questions, "security");
        MessagesSerializer serializer = new SimpleSerializer();
        String msg = serializer.serialize(voting);
        Voting loaded = serializer.deserializeVoting(msg);
        assertEquals(voting.getBeginTimestamp(), loaded.getBeginTimestamp());
        assertEquals(voting.getEndTimestamp(), loaded.getEndTimestamp());
        assertEquals(voting.getId(), loaded.getId());
        assertEquals(voting.getSecurity(), loaded.getSecurity());
        assertEquals(voting.getName(), loaded.getName());
        assertEquals(voting.getQuestions().length, loaded.getQuestions().length);
        assertEquals(voting.getQuestions()[0].getId(), loaded.getQuestions()[0].getId());
        assertEquals(voting.getQuestions()[0].getAnswers().length, loaded.getQuestions()[0].getAnswers().length);
    }
    
    @Test
    @Ignore
    public void testPerformance() throws Exception {
        Iso20022Serializer serializer = new Iso20022Serializer();
        MessagesSerializer simpleSerializer = new SimpleSerializer();
        //deserialize voting
        String votingXml = PropertiesHelper.getResourceString("voting_cumulative.xml", "windows-1251");
        Voting voting = serializer.deserializeVoting(votingXml);
        assertNotNull(voting);

        //deserialization from xml file
        String voteResultXml = PropertiesHelper.getResourceString("voteResult_cumulative.xml", "windows-1251");
        String serializedVoteResult = MessageBuilder.buildMessage(voteResultXml, simpleSerializer.serialize(voting));
        VoteResult voteResult = serializer.deserializeVoteResult(serializedVoteResult);
        assertNotNull(voteResult);

        long start;
        int cnt;

        start = System.currentTimeMillis();
        cnt= 0;
        while(System.currentTimeMillis() - start < 10000) {
            serializer.serialize(voteResult, voting);
            cnt++;
        }
        System.out.println("Serialize: " + cnt/10);

        start = System.currentTimeMillis();
        cnt= 0;
        while(System.currentTimeMillis() - start < 10000) {
            serializer.deserializeVoteResult(serializedVoteResult);
            cnt++;
        }
        System.out.println("Deserialize: " + cnt/10);
    }
}
