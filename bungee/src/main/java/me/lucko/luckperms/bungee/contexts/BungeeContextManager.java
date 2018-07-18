/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package me.lucko.luckperms.bungee.contexts;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import me.lucko.luckperms.api.Contexts;
import me.lucko.luckperms.api.context.ImmutableContextSet;
import me.lucko.luckperms.bungee.LPBungeePlugin;
import me.lucko.luckperms.common.contexts.ContextManager;
import me.lucko.luckperms.common.contexts.ContextsSupplier;

import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.util.concurrent.TimeUnit;

public class BungeeContextManager extends ContextManager<ProxiedPlayer> {

    private final LoadingCache<ProxiedPlayer, Contexts> contextsCache = Caffeine.newBuilder()
            .expireAfterWrite(50, TimeUnit.MILLISECONDS)
            .build(this::calculate);

    public BungeeContextManager(LPBungeePlugin plugin) {
        super(plugin, ProxiedPlayer.class);
    }

    @Override
    public ContextsSupplier getCacheFor(ProxiedPlayer subject) {
        if (subject == null) {
            throw new NullPointerException("subject");
        }

        return new InlineContextsSupplier(subject, this.contextsCache);
    }

    @Override
    public ImmutableContextSet getApplicableContext(ProxiedPlayer subject) {
        return getApplicableContexts(subject).getContexts().makeImmutable();
    }

    @Override
    public Contexts getApplicableContexts(ProxiedPlayer subject) {
        return this.contextsCache.get(subject);
    }

    @Override
    public void invalidateCache(ProxiedPlayer subject) {
        this.contextsCache.invalidate(subject);
    }

    @Override
    public Contexts formContexts(ProxiedPlayer subject, ImmutableContextSet contextSet) {
        return formContexts(contextSet);
    }

    private static final class InlineContextsSupplier implements ContextsSupplier {
        private final ProxiedPlayer key;
        private final LoadingCache<ProxiedPlayer, Contexts> contextsCache;

        private InlineContextsSupplier(ProxiedPlayer key, LoadingCache<ProxiedPlayer, Contexts> contextsCache) {
            this.key = key;
            this.contextsCache = contextsCache;
        }

        @Override
        public Contexts getContexts() {
            return this.contextsCache.get(this.key);
        }

        @Override
        public ImmutableContextSet getContextSet() {
            return getContexts().getContexts().makeImmutable();
        }
    }
}
