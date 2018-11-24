package com.template;

import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.InitiatingFlow;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.utilities.ProgressTracker;

import net.corda.core.contracts.Command;
import net.corda.core.flows.FinalityFlow;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;

// ******************
// * Initiator flow *
// ******************

/**
 * Initiator flow
 *  - @InitiatingFlow means this is part of a flow pair - it triggers the other side to run the
 *    counterpart flow.
 *  - @StartableByRPC allows the node owner to start this flow via an RPC call
 */
@InitiatingFlow
@StartableByRPC
public class IOUFlow extends FlowLogic<Void> {
    private final Integer iouValue;

    // Refers to the borrower in this case since only lenders initiate the flow.
    private final Party otherParty;

    /**
     * The progress tracker provides checkpoints indicating the progress of the flow to observers.
     */
    private final ProgressTracker progressTracker = new ProgressTracker();

    public IOUFlow(Integer iouValue, Party otherParty) {
        this.iouValue = iouValue;
        this.otherParty = otherParty;
    }

    @Override
    public ProgressTracker getProgressTracker() {
        return progressTracker;
    }

    /**
     * The flow logic is encapsulated within the call() method.
     *  - Return type must match type parameter passed to FlowLogic (in this case: Void).
     *  - @Suspendable allows the flow to be check-pointed and serialized to disk when it
     *    encounters a long-running operation. This allows a node to move on to running
     *    other flows.
     */
    @Suspendable
    @Override
    public Void call() throws FlowException {
        // We retrieve the notary identity from the network map.
        Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);

        // We create the transaction components.
        IOUState outputState = new IOUState(iouValue, getOurIdentity(), otherParty);
        // Adding the lenders public key means that for the transaction to be valid,
        // the lender is required to sign the transaction.
        Command command = new Command<>(new TemplateContract.Commands.Action(), getOurIdentity().getOwningKey());

        // We create a transaction builder and add the components.
        // Notice there are no inputs to the transaction: this is because we are not consuming
        // any existing ledger states in the creation of the IOU.
        // Output state requires both the output state itself and a reference to the contract
        // that governs the evolution of state over time. Passing TemplateContract.ID means
        // there are no constraints (temporary).
        TransactionBuilder txBuilder = new TransactionBuilder(notary)
                .addOutputState(outputState, TemplateContract.ID)
                .addCommand(command);

        // Signing the transaction.
        // Once a transaction is signed, no one will be able to modify it without invalidating
        // the signature.
        SignedTransaction signedTx = getServiceHub().signInitialTransaction(txBuilder);

        // Finalising the transaction.
        subFlow(new FinalityFlow(signedTx));

        return null;
    }
}
