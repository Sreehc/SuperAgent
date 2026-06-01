package com.superagent.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.superagent.infra.config.SuperAgentProperties;
import com.superagent.knowledge.service.RecursiveChunker;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecursiveChunkerTest {

    @Test
    void shouldSplitContentIntoOrderedChunksWithOverlap() {
        SuperAgentProperties properties = new SuperAgentProperties();
        properties.getDocumentProcessing().setChunkSize(20);
        properties.getDocumentProcessing().setChunkOverlap(5);

        RecursiveChunker chunker = new RecursiveChunker(properties);
        List<RecursiveChunker.ChunkCandidate> chunks = chunker.chunk("第一段内容比较长，需要切块处理。\n\n第二段继续补充一些说明文本。");

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks.get(0).chunkNo()).isEqualTo(1);
        assertThat(chunks.get(1).chunkNo()).isEqualTo(2);
        assertThat(chunks.get(1).content()).contains(chunks.get(0).content().substring(chunks.get(0).content().length() - 5));
    }
}
