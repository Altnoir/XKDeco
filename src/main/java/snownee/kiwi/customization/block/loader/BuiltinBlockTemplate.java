package snownee.kiwi.customization.block.loader;

import java.util.Optional;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public final class BuiltinBlockTemplate extends KBlockTemplate {
	private final Optional<ResourceLocation> key;
	private MapCodec<Block> codec;

	public BuiltinBlockTemplate(Optional<BlockDefinitionProperties> properties, Optional<ResourceLocation> key) {
		super(properties);
		this.key = key;
	}

	public static Codec<BuiltinBlockTemplate> directCodec(MapCodec<Optional<KMaterial>> materialCodec) {
		return RecordCodecBuilder.create(instance -> instance.group(
						BlockDefinitionProperties.mapCodecField(materialCodec).forGetter(BuiltinBlockTemplate::properties),
						ResourceLocation.CODEC.optionalFieldOf("codec").forGetter(BuiltinBlockTemplate::key))
				.apply(instance, BuiltinBlockTemplate::new));
	}

	@Override
	public KBlockTemplate.Type<?> type() {
		return KBlockTemplates.BUILT_IN.getOrCreate();
	}

	@Override
	public void resolve(ResourceLocation key) {
		codec = BlockCodecs.get(this.key.orElse(key));
	}

	@Override
	public Block createBlock(BlockBehaviour.Properties properties, JsonObject json) {
		if (!json.has(BlockCodecs.BLOCK_PROPERTIES_KEY)) {
			json.add(BlockCodecs.BLOCK_PROPERTIES_KEY, new JsonObject());
		}
		InjectedBlockPropertiesCodec.INJECTED.set(properties);
		DataResult<Block> result = codec.decode(JsonOps.INSTANCE, JsonOps.INSTANCE.getMap(json).result().orElseThrow());
		if (result.error().isPresent()) {
			throw new IllegalStateException(result.error().get().message());
		}
		return result.result().orElseThrow();
	}

	public Optional<ResourceLocation> key() {
		return key;
	}

	@Override
	public String toString() {
		return "BuiltinBlockTemplate[" + "properties=" + properties + ", " + "key=" + key + ", " + "codec=" + codec + ']';
	}

}
