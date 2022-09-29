// SPDX-License-Identifier: MPL-2.0
// Copyright © 2022 Skyline Team and Contributors (https://github.com/skyline-emu/)

#pragma once

#include <boost/container/static_vector.hpp>
#include <gpu/texture/texture.h>
#include "common.h"
#include "packed_pipeline_state.h"
#include "pipeline_manager.h"

namespace skyline::gpu::interconnect::maxwell3d {
    class ColorRenderTargetState : dirty::ManualDirty {
      public:
        struct EngineRegisters {
            const engine::ColorTarget &colorTarget;

            void DirtyBind(DirtyManager &manager, dirty::Handle handle) const;
        };

      private:
        dirty::BoundSubresource<EngineRegisters> engine;
        size_t index;

      public:
        ColorRenderTargetState(dirty::Handle dirtyHandle, DirtyManager &manager, const EngineRegisters &engine, size_t index);

        std::shared_ptr<TextureView> view;

        void Flush(InterconnectContext &ctx, PackedPipelineState &packedState);
    };

    class DepthRenderTargetState : dirty::ManualDirty {
      public:
        struct EngineRegisters {
            const engine::ZtSize &ztSize;
            const soc::gm20b::engine::Address &ztOffset;
            const engine::ZtFormat &ztFormat;
            const engine::ZtBlockSize &ztBlockSize;
            const u32 &ztArrayPitch;
            const engine::ZtSelect &ztSelect;
            const engine::ZtLayer &ztLayer;

            void DirtyBind(DirtyManager &manager, dirty::Handle handle) const;
        };

      private:
        dirty::BoundSubresource<EngineRegisters> engine;

      public:
        DepthRenderTargetState(dirty::Handle dirtyHandle, DirtyManager &manager, const EngineRegisters &engine);

        std::shared_ptr<TextureView> view;

        void Flush(InterconnectContext &ctx, PackedPipelineState &packedState);
    };

    class PipelineStageState : dirty::RefreshableManualDirty {
      public:
        struct EngineRegisters {
            const engine::Pipeline &pipeline;
            const soc::gm20b::engine::Address &programRegion;

            void DirtyBind(DirtyManager &manager, dirty::Handle handle) const;
        };

      private:
        struct CacheEntry {
            ShaderBinary binary;
            u64 hash;

            CacheEntry(ShaderBinary binary, u64 hash) : binary{binary}, hash{hash} {}
        };

        /**
         * @brief Holds mirror state for a single GPU mapped block
         */
        struct MirrorEntry {
            span<u8> mirror;
            tsl::robin_map<u8 *, CacheEntry> cache;
            std::optional<nce::NCE::TrapHandle> trap;
            bool dirty{};
            MirrorEntry(span<u8> alignedMirror) : mirror{alignedMirror} {}
        };

        dirty::BoundSubresource<EngineRegisters> engine;
        engine::Pipeline::Shader::Type shaderType;

        tsl::robin_map<u8 *, std::unique_ptr<MirrorEntry>> mirrorMap;
        std::mutex trapMutex; //!< Protects accesses from trap handlers to the mirror map
        MirrorEntry *entry{};
        span<u8> mirrorBlock{}; //!< Guest mapped memory block corresponding to `entry`

      public:
        ShaderBinary binary;
        u64 hash;

        PipelineStageState(dirty::Handle dirtyHandle, DirtyManager &manager, const EngineRegisters &engine, u8 shaderType);

        ~PipelineStageState();

        void Flush(InterconnectContext &ctx);

        bool Refresh(InterconnectContext &ctx);
    };

    class VertexInputState : dirty::ManualDirty {
      public:
        struct EngineRegisters {
            const std::array<engine::VertexStream, engine::VertexStreamCount> &vertexStreams;
            const std::array<engine::VertexStreamInstance, engine::VertexStreamCount> &vertexStreamInstance;
            const std::array<engine::VertexAttribute, engine::VertexAttributeCount> &vertexAttributes;

            void DirtyBind(DirtyManager &manager, dirty::Handle handle) const;
        };

      private:
        dirty::BoundSubresource<EngineRegisters> engine;

      public:
        VertexInputState(dirty::Handle dirtyHandle, DirtyManager &manager, const EngineRegisters &engine);

        void Flush(PackedPipelineState &packedState);
    };

    class InputAssemblyState {
      public:
        struct EngineRegisters {
            const u32 &primitiveRestartEnable;

            void DirtyBind(DirtyManager &manager, dirty::Handle handle) const;
        };

      private:
        EngineRegisters engine;
        vk::PipelineInputAssemblyStateCreateInfo inputAssemblyState{};
        engine::DrawTopology currentEngineTopology{};

      public:
        InputAssemblyState(const EngineRegisters &engine);

        void Update(PackedPipelineState &packedState);

        void SetPrimitiveTopology(engine::DrawTopology topology);

        engine::DrawTopology GetPrimitiveTopology() const;

        bool NeedsQuadConversion() const;
    };

    class TessellationState {
      public:
        struct EngineRegisters {
            const u32 &patchSize;
            const engine::TessellationParameters &tessellationParameters;

            void DirtyBind(DirtyManager &manager, dirty::Handle handle) const;
        };

      private:
        EngineRegisters engine;

      public:
        TessellationState(const EngineRegisters &engine);

        void Update(PackedPipelineState &packedState);
    };

    /**
     * @brief Holds pipeline state that is directly written by the engine code, without using dirty tracking
     */
    struct DirectPipelineState {
        InputAssemblyState inputAssembly;
    };

    class RasterizationState : dirty::ManualDirty {
      public:
        struct EngineRegisters {
            const u32 &rasterEnable;
            const engine::PolygonMode &frontPolygonMode;
            const engine::PolygonMode &backPolygonMode;
            const u32 &oglCullEnable;
            const engine::CullFace &oglCullFace;
            const engine::WindowOrigin &windowOrigin;
            const engine::FrontFace &oglFrontFace;
            const engine::ViewportClipControl &viewportClipControl;
            const engine::PolyOffset &polyOffset;
            const engine::ProvokingVertex &provokingVertex;
            const float &pointSize;
            const engine::ZClipRange &zClipRange;

            void DirtyBind(DirtyManager &manager, dirty::Handle handle) const;
        };

      private:
        dirty::BoundSubresource<EngineRegisters> engine;

      public:
        vk::StructureChain<vk::PipelineRasterizationStateCreateInfo, vk::PipelineRasterizationProvokingVertexStateCreateInfoEXT> rasterizationState{};

        RasterizationState(dirty::Handle dirtyHandle, DirtyManager &manager, const EngineRegisters &engine);

        void Flush(PackedPipelineState &packedState);
    };

    class DepthStencilState : dirty::ManualDirty {
      public:
        struct EngineRegisters {
            const u32 &depthTestEnable;
            const u32 &depthWriteEnable;
            const engine::CompareFunc &depthFunc;
            const u32 &depthBoundsTestEnable;
            const u32 &stencilTestEnable;
            const u32 &twoSidedStencilTestEnable;
            const engine::StencilOps &stencilOps;
            const engine::StencilOps &stencilBack;

            void DirtyBind(DirtyManager &manager, dirty::Handle handle) const;
        };

      private:
        dirty::BoundSubresource<EngineRegisters> engine;

      public:
        DepthStencilState(dirty::Handle dirtyHandle, DirtyManager &manager, const EngineRegisters &engine);

        void Flush(PackedPipelineState &packedState);
    };

    class ColorBlendState : dirty::ManualDirty {
      public:
        struct EngineRegisters {
            const engine::LogicOp &logicOp;
            const u32 &singleCtWriteControl;
            const std::array<engine::CtWrite, engine::ColorTargetCount> &ctWrites;
            const u32 &blendStatePerTargetEnable;
            const std::array<engine::BlendPerTarget, engine::ColorTargetCount> &blendPerTargets;
            const engine::Blend &blend;

            void DirtyBind(DirtyManager &manager, dirty::Handle handle) const;
        };

      private:
        dirty::BoundSubresource<EngineRegisters> engine;

      public:
        ColorBlendState(dirty::Handle dirtyHandle, DirtyManager &manager, const EngineRegisters &engine);

        void Flush(PackedPipelineState &packedState);
    };

    class GlobalShaderConfigState {
      public:
        struct EngineRegisters {
            const std::array<u32, 8> &postVtgShaderAttributeSkipMask;
            const engine::BindlessTexture &bindlessTexture;
            const u32 &apiMandatedEarlyZ;

            void DirtyBind(DirtyManager &manager, dirty::Handle handle) const;
        };

      private:
        EngineRegisters engine;

      public:
        GlobalShaderConfigState(const EngineRegisters &engine);

        void Update(PackedPipelineState &packedState);
    };

    /**
     * @brief Holds all GPU state for a pipeline, any changes to this will result in a pipeline cache lookup
     */
    class PipelineState : dirty::CachedManualDirty {
      public:
        struct EngineRegisters {
            std::array<PipelineStageState::EngineRegisters, engine::PipelineCount> pipelineStageRegisters;
            std::array<ColorRenderTargetState::EngineRegisters, engine::ColorTargetCount> colorRenderTargetsRegisters;
            DepthRenderTargetState::EngineRegisters depthRenderTargetRegisters;
            VertexInputState::EngineRegisters vertexInputRegisters;
            InputAssemblyState::EngineRegisters inputAssemblyRegisters;
            TessellationState::EngineRegisters tessellationRegisters;
            RasterizationState::EngineRegisters rasterizationRegisters;
            DepthStencilState::EngineRegisters depthStencilRegisters;
            ColorBlendState::EngineRegisters colorBlendRegisters;
            GlobalShaderConfigState::EngineRegisters globalShaderConfigRegisters;
            const engine::CtSelect &ctSelect;

            void DirtyBind(DirtyManager &manager, dirty::Handle handle) const;
        };

      private:
        PipelineManager pipelineManager{};

        PackedPipelineState packedState{};

        dirty::BoundSubresource<EngineRegisters> engine;

        std::array<dirty::ManualDirtyState<PipelineStageState>, engine::PipelineCount> pipelineStages;
        std::array<dirty::ManualDirtyState<ColorRenderTargetState>, engine::ColorTargetCount> colorRenderTargets;
        dirty::ManualDirtyState<DepthRenderTargetState> depthRenderTarget;
        dirty::ManualDirtyState<VertexInputState> vertexInput;
        TessellationState tessellation;
        dirty::ManualDirtyState<RasterizationState> rasterization;
        dirty::ManualDirtyState<DepthStencilState> depthStencil;
        dirty::ManualDirtyState<ColorBlendState> colorBlend;
        GlobalShaderConfigState globalShaderConfig;
        const engine::CtSelect &ctSelect;

      public:
        DirectPipelineState directState;
        Pipeline *pipeline{};
        boost::container::static_vector<TextureView *, engine::ColorTargetCount> colorAttachments;
        TextureView *depthAttachment{};

        PipelineState(dirty::Handle dirtyHandle, DirtyManager &manager, const EngineRegisters &engine);

        void Flush(InterconnectContext &ctx, StateUpdateBuilder &builder);

        void PurgeCaches();

        std::shared_ptr<TextureView> GetColorRenderTargetForClear(InterconnectContext &ctx, size_t index);

        std::shared_ptr<TextureView> GetDepthRenderTargetForClear(InterconnectContext &ctx);
    };
}
