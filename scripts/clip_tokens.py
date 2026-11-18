"""Build OpenCLIP token sequences for the MobileCLIP2 ONNX text encoders."""
import numpy as np

CLIP_BOS = 49406
CLIP_EOS = 49407
CLIP_PAD = 0
CLIP_CONTEXT = 77


def encode_prompts(tokenizer, prompts):
    """Return (input_ids, attention_mask) arrays shaped [len(prompts), 77].

    OpenCLIP writes BOS, the BPE body and one EOS, then fills the rest of the
    context with zeros. The MobileCLIP2 text towers are trained without a
    causal mask, so every position attends to the padding and the pad value
    is part of the model input. Padding with EOS, as the Hugging Face CLIP
    tokenizer does, changes the embeddings and breaks image/text alignment.
    """
    encoded = tokenizer.encode_batch(list(prompts), add_special_tokens=False)
    tokens = np.full((len(encoded), CLIP_CONTEXT), CLIP_PAD, dtype=np.int64)
    masks = np.zeros_like(tokens)
    for index, item in enumerate(encoded):
        body = item.ids
        if len(body) > CLIP_CONTEXT - 2:
            raise RuntimeError("Prompt exceeds CLIP context: " + prompts[index])
        tokens[index, 0] = CLIP_BOS
        tokens[index, 1 : len(body) + 1] = body
        tokens[index, len(body) + 1] = CLIP_EOS
        masks[index, : len(body) + 2] = 1
    lengths = masks.sum(axis=1)
    assert np.all(tokens[:, 0] == CLIP_BOS)
    assert np.all(tokens[np.arange(len(encoded)), lengths - 1] == CLIP_EOS)
    assert np.all((tokens == CLIP_EOS).sum(axis=1) == 1)
    assert np.all(tokens[masks == 0] == CLIP_PAD)
    return tokens, masks
