/*
 * Copyright (c) 2021 LambdAurora <aurora42lambda@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package dev.lambdaurora.aurorasdeco.block;

import dev.lambdaurora.aurorasdeco.AurorasDeco;
import dev.lambdaurora.aurorasdeco.registry.WoodType;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.Waterloggable;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Represents a bench.
 *
 * @author LambdAurora
 * @version 1.0.0
 * @since 1.0.0
 */
public class BenchBlock extends Block implements SeatBlock, Waterloggable {
    public static final EnumProperty<Direction> FACING = Properties.FACING;
    public static final BooleanProperty LEFT_LEGS = BooleanProperty.of("left_legs");
    public static final BooleanProperty RIGHT_LEGS = BooleanProperty.of("right_legs");
    public static final BooleanProperty REST = BooleanProperty.of("rest");
    public static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED;

    public static final Identifier BENCH_SEAT_MODEL = AurorasDeco.id("block/template/bench_seat");
    public static final Identifier BENCH_LEGS_MODEL = AurorasDeco.id("block/template/bench_legs");
    public static final Identifier BENCH_REST_PLANK_MODEL = AurorasDeco.id("block/template/bench_rest_plank");
    public static final Identifier BENCH_REST_LEFT_MODEL = AurorasDeco.id("block/template/bench_rest_left");
    public static final Identifier BENCH_REST_RIGHT_MODEL = AurorasDeco.id("block/template/bench_rest_right");
    public static final Identifier BENCH_BETTERGRASS_DATA = AurorasDeco.id("bettergrass/data/bench");

    private static final List<BenchBlock> BENCHES = new ArrayList<>();

    protected static final VoxelShape X_SHAPE = createCuboidShape(0, 0, 2, 16, 8, 14);
    protected static final VoxelShape Z_SHAPE = createCuboidShape(2, 0, 0, 14, 8, 16);

    private final WoodType woodType;

    public BenchBlock(WoodType woodType) {
        super(settings(woodType));
        this.woodType = woodType;

        this.setDefaultState(this.getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(LEFT_LEGS, true)
                .with(RIGHT_LEGS, true)
                .with(REST, false)
                .with(WATERLOGGED, false)
        );

        BENCHES.add(this);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, LEFT_LEGS, RIGHT_LEGS, REST, WATERLOGGED);
    }

    public static Stream<BenchBlock> streamBenches() {
        return BENCHES.stream();
    }

    public WoodType getWoodType() {
        return this.woodType;
    }

    @Override
    public float getSitYOffset() {
        return 0.3f;
    }

    private <T extends Comparable<T>, P extends Property<T>> P getPropertyTowards(Direction facing, Direction towards,
                                                                                  P left, P right) {
        return switch (facing) {
            default -> towards == Direction.WEST ? right : left;
            case EAST -> towards == Direction.NORTH ? right : left;
            case SOUTH -> towards == Direction.EAST ? right : left;
            case WEST -> towards == Direction.SOUTH ? right : left;
        };
    }

    private BooleanProperty getLegsTowards(Direction facing, Direction towards) {
        return this.getPropertyTowards(facing, towards, LEFT_LEGS, RIGHT_LEGS);
    }

    /**
     * {@return whether the bench state has a rest or not}
     *
     * @param state the bench block state
     */
    private static boolean hasRest(BlockState state) {
        return state.getBlock() instanceof BenchBlock && state.get(REST);
    }

    /**
     * Returns whether this bench can connect to the given block state.
     *
     * @param other the other block to try to connect to
     * @param benchFacing this bench facing direction
     * @return {@code true} if this bench can connect to the given block, otherwise {@code false}
     */
    public boolean canConnect(BlockState other, Direction benchFacing, boolean hasRest) {
        return other.getBlock() == this && benchFacing == other.get(FACING)
                && (hasRest == hasRest(other));
    }

    /* Shapes */

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return state.get(FACING).getAxis() == Direction.Axis.X ? Z_SHAPE : X_SHAPE;
    }

    /* Placement */

    @Override
    public @Nullable BlockState getPlacementState(ItemPlacementContext ctx) {
        var world = ctx.getWorld();
        var pos = ctx.getBlockPos();
        var fluid = world.getFluidState(pos);

        var facing = ctx.getPlayerFacing().getOpposite();

        var relativeRight = pos.offset(facing.rotateYCounterclockwise());
        var relativeLeft = pos.offset(facing.rotateYClockwise());

        return this.getDefaultState()
                .with(FACING, facing)
                .with(LEFT_LEGS, !this.canConnect(world.getBlockState(relativeLeft), facing, false))
                .with(RIGHT_LEGS, !this.canConnect(world.getBlockState(relativeRight), facing, false))
                .with(WATERLOGGED, fluid.getFluid() == Fluids.WATER);
    }

    /* Updates */

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState newState,
                                                WorldAccess world, BlockPos pos, BlockPos posFrom) {
        if (state.get(WATERLOGGED)) {
            world.getFluidTickScheduler().schedule(pos, Fluids.WATER, Fluids.WATER.getTickRate(world));
        }

        var newSelf = super.getStateForNeighborUpdate(state, direction, newState, world, pos, posFrom);
        var benchFacing = state.get(FACING);

        if (direction.getAxis().isHorizontal() && direction.getAxis() != benchFacing.getAxis()) {
            newSelf = newSelf.with(this.getLegsTowards(benchFacing, direction),
                    !this.canConnect(newState, benchFacing, hasRest(newSelf)));
        }

        return newSelf;
    }

    /* Interaction */

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand,
                              BlockHitResult hit) {
        var stack = player.getStackInHand(hand);
        if (this.sit(world, pos, state, player, stack))
            return ActionResult.success(world.isClient());
        return super.onUse(state, world, pos, player, hand, hit);
    }

    /* Fluid */

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.get(WATERLOGGED) ? Fluids.WATER.getStill(false) : super.getFluidState(state);
    }

    private static FabricBlockSettings settings(WoodType woodType) {
        var planks = woodType.getComponent(WoodType.ComponentType.PLANKS);
        if (planks == null) throw new IllegalStateException("BenchBlock attempted to be created while the wood type is invalid.");
        return FabricBlockSettings.copyOf(planks.block())
                .nonOpaque();
    }
}
