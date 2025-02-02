package com.minecolonies.coremod.entity.citizen.citizenhandlers;

import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.citizen.citizenhandlers.ICitizenSleepHandler;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.EntityUtils;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.coremod.colony.interactionhandling.SimpleNotificationInteraction;
import com.minecolonies.coremod.colony.interactionhandling.StandardInteraction;
import com.minecolonies.coremod.colony.jobs.JobMiner;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Pose;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Optional;

import static com.minecolonies.api.entity.citizen.AbstractEntityCitizen.DATA_BED_POS;
import static com.minecolonies.api.entity.citizen.AbstractEntityCitizen.DATA_IS_ASLEEP;
import static com.minecolonies.api.research.util.ResearchConstants.WORK_LONGER;
import static com.minecolonies.api.util.constant.CitizenConstants.NIGHT;
import static com.minecolonies.api.util.constant.Constants.*;
import static com.minecolonies.api.util.constant.TranslationConstants.COM_MINECOLONIES_COREMOD_ENTITY_CITIZEN_SLEEPING;

/**
 * Handles the sleep of the citizen.
 */
public class CitizenSleepHandler implements ICitizenSleepHandler
{
    /**
     * The additional weight for Y diff
     */
    private static final double Y_DIFF_WEIGHT = 1.5;

    /**
     * The rough time traveling one block takes, in ticks
     */
    private static final double TIME_PER_BLOCK           = 6;
    private static final double MAX_NO_COMPLAIN_DISTANCE = 160;

    /**
     * The citizen assigned to this manager.
     */
    private final AbstractEntityCitizen citizen;

    /**
     * Constructor for the experience handler.
     *
     * @param citizen the citizen owning the handler.
     */
    public CitizenSleepHandler(final AbstractEntityCitizen citizen)
    {
        this.citizen = citizen;
    }

    /**
     * Is the citizen a sleep?
     *
     * @return true when a sleep.
     */
    @Override
    public boolean isAsleep()
    {
        return citizen.getEntityData().get(DATA_IS_ASLEEP);
    }

    /**
     * Sets if the citizen is a sleep. Caution: Use trySleep(BlockPos) for better control
     *
     * @param isAsleep True to make the citizen sleep.
     */
    private void setIsAsleep(final boolean isAsleep)
    {
        if (citizen.getCitizenData() != null)
        {
            citizen.getCitizenData().setAsleep(isAsleep);
        }
        citizen.getEntityData().set(DATA_IS_ASLEEP, isAsleep);
    }

    /**
     * Attempts a sleep interaction with the citizen and the given bed.
     *
     * @param bedLocation The possible location to sleep.
     */
    @Override
    public boolean trySleep(final BlockPos bedLocation)
    {
        final BlockState state = WorldUtil.isEntityBlockLoaded(citizen.level, bedLocation) ? citizen.level.getBlockState(bedLocation) : null;
        final boolean isBed = state != null && state.getBlock().isBed(state, citizen.level, bedLocation, citizen);

        if (!isBed)
        {
            return false;
        }

        citizen.updatePose(Pose.SLEEPING);
        citizen.getNavigation().stop();
        citizen.setPos(((float) bedLocation.getX() + HALF_BLOCK),
          (float) bedLocation.getY() + 0.8F,
          ((float) bedLocation.getZ() + HALF_BLOCK));
        citizen.setSleepingPos(bedLocation);

        citizen.setDeltaMovement(Vector3d.ZERO);
        citizen.hasImpulse = true;

        //Remove item while citizen is asleep.
        citizen.getCitizenItemHandler().removeHeldItem();

        setIsAsleep(true);

        citizen.getCitizenData().triggerInteraction(new StandardInteraction(new TranslationTextComponent(COM_MINECOLONIES_COREMOD_ENTITY_CITIZEN_SLEEPING), ChatPriority.HIDDEN));

        if (citizen.getCitizenData() != null)
        {
            citizen.getCitizenData().setBedPos(bedLocation);
        }
        citizen.getEntityData().set(DATA_BED_POS, bedLocation);

        citizen.getCitizenData().getColony().getCitizenManager().onCitizenSleep();

        return true;
    }

    /**
     * Called when the citizen wakes up.
     */
    @Override
    public void onWakeUp()
    {
        notifyCitizenHandlersOfWakeUp();

        //Only do this if he really sleeps
        if (!isAsleep())
        {
            return;
        }
        citizen.updatePose(Pose.STANDING);
        citizen.clearSleepingPos();
        spawnCitizenFromBed();
    }

    private void notifyCitizenHandlersOfWakeUp()
    {
        if (citizen.getCitizenColonyHandler().getWorkBuilding() != null)
        {
            citizen.getCitizenStatusHandler().setLatestStatus(new TranslationTextComponent("com.minecolonies.coremod.status.working"));
            citizen.getCitizenColonyHandler().getWorkBuilding().onWakeUp();
        }
        if (citizen.getCitizenJobHandler().getColonyJob() != null)
        {
            citizen.getCitizenJobHandler().getColonyJob().onWakeUp();
        }

        final IBuilding homeBuilding = citizen.getCitizenColonyHandler().getHomeBuilding();
        if (homeBuilding != null)
        {
            homeBuilding.onWakeUp();
        }
    }

    private void spawnCitizenFromBed()
    {
        final BlockPos spawn;
        if (!getBedLocation().equals(BlockPos.ZERO) && citizen.level.getBlockState(getBedLocation()).getBlock().is(BlockTags.BEDS))
        {
            spawn = EntityUtils.getSpawnPoint(citizen.level, getBedLocation());
        }
        else
        {
            spawn = citizen.blockPosition();
        }

        if (spawn != null && !spawn.equals(BlockPos.ZERO))
        {
            citizen.setPos(spawn.getX() + HALF_BLOCK, spawn.getY() + HALF_BLOCK, spawn.getZ() + HALF_BLOCK);
        }

        setIsAsleep(false);
        if (citizen.getCitizenData() != null)
        {
            citizen.getCitizenData().setBedPos(new BlockPos(0, 0, 0));
        }
        citizen.getEntityData().set(DATA_BED_POS, new BlockPos(0, 0, 0));
    }

    @Override
    public BlockPos findHomePos()
    {
        final BlockPos pos = citizen.getRestrictCenter();
        if (pos.equals(BlockPos.ZERO))
        {
            if (citizen.getCitizenColonyHandler().getColony().hasTownHall())
            {
                return citizen.getCitizenColonyHandler().getColony().getBuildingManager().getTownHall().getPosition();
            }

            return citizen.getCitizenColonyHandler().getColony().getCenter();
        }

        return pos;
    }

    /**
     * Get the bed location of the citizen.
     *
     * @return the bed location.
     */
    @Override
    public BlockPos getBedLocation()
    {
        return citizen.getEntityData().get(DATA_BED_POS);
    }

    @Override
    public boolean shouldGoSleep()
    {
        final BlockPos homePos = findHomePos();
        BlockPos citizenPos = citizen.blockPosition();

        int additionalDist = 0;

        // Additional distance for miners
        if (citizen.getCitizenData().getJob() instanceof JobMiner && citizen.getCitizenData().getWorkBuilding().getPosition().getY() - 20 > citizenPos.getY())
        {
            final BlockPos workPos = citizen.getCitizenData().getWorkBuilding().getID();
            additionalDist = (int) BlockPosUtil.getDistance2D(citizenPos, workPos) + Math.abs(citizenPos.getY() - workPos.getY()) * 3;
            citizenPos = workPos;
        }

        // Calc distance with some y weight
        final int xDiff = Math.abs(homePos.getX() - citizenPos.getX());
        final int zDiff = Math.abs(homePos.getZ() - citizenPos.getZ());
        final int yDiff = (int) (Math.abs(homePos.getY() - citizenPos.getY()) * Y_DIFF_WEIGHT);

        final double timeNeeded = (Math.sqrt(xDiff * xDiff + zDiff * zDiff + yDiff * yDiff) + additionalDist) * TIME_PER_BLOCK;

        // Estimated arrival is 1hour past night
        final double timeLeft = (citizen.getCitizenColonyHandler().getColony().getResearchManager().getResearchEffects().getEffectStrength(WORK_LONGER) == 0
                                   ? NIGHT : NIGHT + citizen.getCitizenColonyHandler().getColony().getResearchManager().getResearchEffects().getEffectStrength(WORK_LONGER) * 1000) - (citizen.level.getDayTime() % 24000);
        if (timeLeft <= 0 || (timeLeft - timeNeeded <= 0))
        {
            if (citizen.getCitizenData().getWorkBuilding() != null)
            {
                final double workHomeDistance = Math.sqrt(BlockPosUtil.getDistanceSquared(homePos, citizen.getCitizenData().getWorkBuilding().getID()));
                if (workHomeDistance > MAX_NO_COMPLAIN_DISTANCE)
                {
                    citizen.getCitizenData()
                      .triggerInteraction(new SimpleNotificationInteraction(new TranslationTextComponent("com.minecolonies.coremod.gui.chat.hometoofar"), ChatPriority.IMPORTANT));
                }
            }
            return true;
        }

        return false;
    }
}
