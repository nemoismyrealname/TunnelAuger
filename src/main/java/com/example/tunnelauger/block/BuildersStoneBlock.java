package com.example.tunnelauger.block;

import org.jetbrains.annotations.Nullable;

import com.example.tunnelauger.block.entity.BuildersStoneBlockEntity;
import com.example.tunnelauger.block.entity.ModBlockEntities;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Блок ничего "не решает" сам — вся логика ритуала живёт в
 * BuildersStoneBlockEntity.tick(). Блок только создаёт блок-энтити
 * и подключает её к тикеру.
 */
public class BuildersStoneBlock extends BaseEntityBlock {

    public BuildersStoneBlock(Properties settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(BuildersStoneBlock::new);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BuildersStoneBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.BUILDERS_STONE_ENTITY, BuildersStoneBlockEntity::tick);
    }
}
